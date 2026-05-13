#include "audio_engine.h"

#include <aaudio/AAudio.h>
#include <arpa/inet.h>
#include <math.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#define RB_MASK(x) ((x) & (rb->capacity - 1))

bool rb_init(RingBuf *rb, int capacity) {
    if (capacity < 2 || (capacity & (capacity - 1))) return false;
    rb->data = (int16_t *)calloc(capacity, sizeof(int16_t));
    if (!rb->data) return false;
    rb->capacity = capacity;
    rb->write_idx = 0;
    rb->read_idx = 0;
    return true;
}

void rb_destroy(RingBuf *rb) {
    free(rb->data);
    rb->data = NULL;
}

int rb_write(RingBuf *rb, const int16_t *data, int samples) {
    int filled = rb->write_idx - rb->read_idx;
    int space = rb->capacity - filled;
    if (space <= 0) return 0;
    if (samples > space) samples = space;

    int pos = RB_MASK(rb->write_idx);
    int first = rb->capacity - pos;
    if (first > samples) first = samples;

    memcpy(rb->data + pos, data, first * sizeof(int16_t));
    if (first < samples)
        memcpy(rb->data, data + first, (samples - first) * sizeof(int16_t));

    __atomic_add_fetch(&rb->write_idx, samples, __ATOMIC_RELEASE);
    return samples;
}

int rb_read(RingBuf *rb, int16_t *data, int samples) {
    int filled = rb->write_idx - rb->read_idx;
    if (filled <= 0) return 0;
    if (samples > filled) samples = filled;

    int pos = RB_MASK(rb->read_idx);
    int first = rb->capacity - pos;
    if (first > samples) first = samples;

    memcpy(data, rb->data + pos, first * sizeof(int16_t));
    if (first < samples)
        memcpy(data + first, rb->data, (samples - first) * sizeof(int16_t));

    __atomic_add_fetch(&rb->read_idx, samples, __ATOMIC_RELEASE);
    return samples;
}

static float calc_rms(const int16_t *data, int samples) {
    if (samples <= 0) return 0;
    double sum = 0;
    int step = samples > 1024 ? samples / 256 : 1;
    for (int i = 0; i < samples; i += step) {
        int64_t s = data[i];
        sum += (double)(s * s);
    }
    return (float)sqrt(sum / ((samples + step - 1) / step));
}

static aaudio_data_callback_result_t cap_cb(AAudioStream *stream, void *ud,
                                             void *audio, int32_t frames) {
    (void)stream;
    AudioEngine *e = (AudioEngine *)ud;
    if (!e->running) return AAUDIO_CALLBACK_RESULT_STOP;

    int16_t *samples = (int16_t *)audio;
    int n = frames;
    int written = rb_write(&e->mic_rb, samples, n);

    float rms = calc_rms(samples, n);
    e->mic_level = e->mic_level * 0.5f + (rms / 32768.0f * 100.0f) * 0.5f;

    (void)written;
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

static aaudio_data_callback_result_t play_cb(AAudioStream *stream, void *ud,
                                              void *audio, int32_t frames) {
    (void)stream;
    AudioEngine *e = (AudioEngine *)ud;
    if (!e->running) return AAUDIO_CALLBACK_RESULT_STOP;

    int16_t *samples = (int16_t *)audio;
    int n = frames;
    int read = rb_read(&e->spk_rb, samples, n);

    if (read < n)
        memset(samples + read, 0, (n - read) * sizeof(int16_t));

    float rms = calc_rms(samples, n);
    e->spk_level = e->spk_level * 0.5f + (rms / 32768.0f * 100.0f) * 0.5f;

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

static void *send_pcm(void *arg) {
    AudioEngine *e = (AudioEngine *)arg;

    struct sched_param sp = {.sched_priority = 10};
    pthread_setschedparam(pthread_self(), SCHED_FIFO, &sp);

    uint8_t pkt[HEADER_SIZE + 1024];
    int16_t buf[1024];

    while (e->running) {
        int samples = rb_read(&e->mic_rb, buf, e->frame_size);
        if (samples <= 0) {
            usleep(2000);
            continue;
        }

        int pcm_bytes = samples * 2;

        pkt[0] = (uint8_t)(MAGIC & 0xFF);
        pkt[1] = (uint8_t)((MAGIC >> 8) & 0xFF);
        pkt[2] = (uint8_t)((e->seq >> 8) & 0xFF);
        pkt[3] = (uint8_t)(e->seq & 0xFF);
        e->seq++;
        pkt[4] = 0; pkt[5] = 0; pkt[6] = 0; pkt[7] = 0;
        pkt[8] = (uint8_t)(pcm_bytes & 0xFF);
        pkt[9] = (uint8_t)((pcm_bytes >> 8) & 0xFF);
        pkt[10] = (uint8_t)((pcm_bytes >> 16) & 0xFF);
        pkt[11] = (uint8_t)((pcm_bytes >> 24) & 0xFF);
        pkt[12] = STREAM_MIC;

        memcpy(pkt + HEADER_SIZE, buf, pcm_bytes);

        ssize_t sent = sendto(e->mic_fd, pkt, HEADER_SIZE + pcm_bytes, 0,
                              (struct sockaddr *)&e->srv_addr, sizeof(e->srv_addr));
        if (sent > 0)
            __atomic_add_fetch(&e->tx_bytes, sent, __ATOMIC_RELAXED);
    }
    return NULL;
}

static void *recv_pcm(void *arg) {
    AudioEngine *e = (AudioEngine *)arg;

    struct sched_param sp = {.sched_priority = 10};
    pthread_setschedparam(pthread_self(), SCHED_FIFO, &sp);

    uint8_t pkt[HEADER_SIZE + 4096];
    struct sockaddr_in from;
    socklen_t flen = sizeof(from);

    while (e->running) {
        fd_set rfds;
        FD_ZERO(&rfds);
        FD_SET(e->spk_fd, &rfds);
        struct timeval tv = {.tv_sec = 0, .tv_usec = 500000};
        int r = select(e->spk_fd + 1, &rfds, NULL, NULL, &tv);
        if (r <= 0) continue;

        int n = (int)recvfrom(e->spk_fd, pkt, sizeof(pkt), 0,
                               (struct sockaddr *)&from, &flen);
        if (n < HEADER_SIZE) continue;

        int pcm_len = pkt[8] | (pkt[9] << 8) | (pkt[10] << 16) | (pkt[11] << 24);
        if (n < HEADER_SIZE + pcm_len || pkt[12] != STREAM_SPEAKER) continue;

        rb_write(&e->spk_rb, (int16_t *)(pkt + HEADER_SIZE), pcm_len / 2);
        __atomic_add_fetch(&e->rx_bytes, n, __ATOMIC_RELAXED);
    }
    return NULL;
}

bool engine_start(AudioEngine *e, const char *ip, int mic_port, int spk_port,
                  int sr, int frames) {
    e->sample_rate = sr;
    e->frame_size = frames;
    e->seq = 0;

    if (!rb_init(&e->mic_rb, 8192)) return false;
    if (!rb_init(&e->spk_rb, 8192)) { rb_destroy(&e->mic_rb); return false; }

    e->srv_addr.sin_family = AF_INET;
    e->srv_addr.sin_port = htons(mic_port);
    if (inet_pton(AF_INET, ip, &e->srv_addr.sin_addr) <= 0)
        { rb_destroy(&e->mic_rb); rb_destroy(&e->spk_rb); return false; }

    e->mic_fd = (int)socket(AF_INET, SOCK_DGRAM, 0);
    if (e->mic_fd < 0) { rb_destroy(&e->mic_rb); rb_destroy(&e->spk_rb); return false; }

    e->spk_fd = (int)socket(AF_INET, SOCK_DGRAM, 0);
    if (e->spk_fd < 0) {
        close(e->mic_fd); rb_destroy(&e->mic_rb); rb_destroy(&e->spk_rb);
        return false;
    }

    struct sockaddr_in local = {0};
    local.sin_family = AF_INET;
    local.sin_addr.s_addr = INADDR_ANY;
    local.sin_port = htons(spk_port);
    if (bind(e->spk_fd, (struct sockaddr *)&local, sizeof(local)) < 0) {
        close(e->mic_fd); close(e->spk_fd);
        rb_destroy(&e->mic_rb); rb_destroy(&e->spk_rb);
        return false;
    }

    AAudioStreamBuilder *builder = NULL;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK || !builder) goto fail;

    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setSampleRate(builder, sr);
    AAudioStreamBuilder_setChannelCount(builder, 1);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setDataCallback(builder, cap_cb, e);
    AAudioStreamBuilder_setBufferCapacityInFrames(builder, frames * 2);

    result = AAudioStreamBuilder_openStream(builder, (AAudioStream **)&e->cap_stream);
    if (result != AAUDIO_OK) { AAudioStreamBuilder_delete(builder); goto fail; }

    AAudioStreamBuilder_setDataCallback(builder, play_cb, e);
    result = AAudioStreamBuilder_openStream(builder, (AAudioStream **)&e->play_stream);
    if (result != AAUDIO_OK) {
        AAudioStream_close((AAudioStream *)e->cap_stream);
        AAudioStreamBuilder_delete(builder);
        goto fail;
    }
    AAudioStreamBuilder_delete(builder);

    result = AAudioStream_requestStart((AAudioStream *)e->cap_stream);
    if (result != AAUDIO_OK) goto fail;
    result = AAudioStream_requestStart((AAudioStream *)e->play_stream);
    if (result != AAUDIO_OK) { AAudioStream_requestStop((AAudioStream *)e->cap_stream); goto fail; }

    e->running = true;
    pthread_create(&e->send_thr, NULL, send_pcm, e);
    pthread_create(&e->recv_thr, NULL, recv_pcm, e);

    return true;

fail:
    close(e->mic_fd); close(e->spk_fd);
    if (e->cap_stream) AAudioStream_close((AAudioStream *)e->cap_stream);
    if (e->play_stream) AAudioStream_close((AAudioStream *)e->play_stream);
    rb_destroy(&e->mic_rb); rb_destroy(&e->spk_rb);
    memset(e, 0, sizeof(*e));
    return false;
}

void engine_stop(AudioEngine *e) {
    if (!e->running) return;
    e->running = false;

    if (e->send_thr) pthread_join(e->send_thr, NULL);
    if (e->recv_thr) pthread_join(e->recv_thr, NULL);

    if (e->cap_stream) {
        AAudioStream_requestStop((AAudioStream *)e->cap_stream);
        AAudioStream_close((AAudioStream *)e->cap_stream);
    }
    if (e->play_stream) {
        AAudioStream_requestStop((AAudioStream *)e->play_stream);
        AAudioStream_close((AAudioStream *)e->play_stream);
    }

    if (e->mic_fd > 0) close(e->mic_fd);
    if (e->spk_fd > 0) close(e->spk_fd);

    rb_destroy(&e->mic_rb);
    rb_destroy(&e->spk_rb);
    memset(e, 0, sizeof(*e));
}
