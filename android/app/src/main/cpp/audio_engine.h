#ifndef AUDIO_ENGINE_H
#define AUDIO_ENGINE_H

#include <stdint.h>
#include <stdbool.h>
#include <netinet/in.h>
#include <pthread.h>

#define HEADER_SIZE 13
#define MAGIC 0xABCD
#define STREAM_MIC 0
#define STREAM_SPEAKER 1

typedef struct {
    int16_t *data;
    int capacity;
    volatile unsigned int write_idx;
    volatile unsigned int read_idx;
} RingBuf;

typedef struct {
    volatile bool running;
    int sample_rate;
    int frame_size;
    uint16_t seq;

    void *cap_stream;
    void *play_stream;

    int mic_fd;
    int spk_fd;
    struct sockaddr_in srv_addr;

    RingBuf mic_rb;
    RingBuf spk_rb;

    pthread_t send_thr;
    pthread_t recv_thr;

    volatile float mic_level;
    volatile float spk_level;
    volatile int64_t tx_bytes;
    volatile int64_t rx_bytes;
} AudioEngine;

bool rb_init(RingBuf *rb, int capacity);
void rb_destroy(RingBuf *rb);
int rb_write(RingBuf *rb, const int16_t *data, int samples);
int rb_read(RingBuf *rb, int16_t *data, int samples);

bool engine_start(AudioEngine *e, const char *ip, int mic_port, int spk_port, int sr, int frames);
void engine_stop(AudioEngine *e);

#endif
