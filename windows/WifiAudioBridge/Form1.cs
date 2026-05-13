using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using NAudio.CoreAudioApi;
using NAudio.Wave;

namespace WifiAudioBridge;

public partial class Form1 : Form
{
    private const int UdpPort = 9999;
    private const int TcpPort = 8888;
    private const int MicUdpPort = 8890;
    private const int SpkUdpPort = 8891;
    private const int SampleRate = 44100;
    private const int HeaderSize = 13;
    private const ushort Magic = 0xABCD;

    private UdpClient? _udpDiscovery;
    private TcpListener? _tcpListener;
    private UdpClient? _udpMic;
    private CancellationTokenSource? _cts;
    private volatile bool _running;
    private volatile bool _hasClient;

    private long _totalTxBytes;
    private long _totalRxBytes;
    private long _lastTxBytes;
    private long _lastRxBytes;
    private DateTime _lastStatsTime = DateTime.Now;
    private ushort _seqSpeaker;
    private readonly object _statsLock = new();
    private readonly object _levelLock = new();
    private float _micLevelSmooth;
    private float _spkLevelSmooth;
    private volatile bool _micMuted;
    private volatile bool _spkMuted;
    private DateTime _lastBufferUpdate = DateTime.MinValue;
    private volatile bool _clientConnected;
    private System.Net.IPEndPoint? _clientEp;

    private readonly object _clientLock = new();
    private BufferedWaveProvider? _waveProvider;

    private Label _lblStatus;
    private Label _lblIp;
    private Label _lblMicLevel;
    private ProgressBar _barMic;
    private Button _btnMicMute;
    private Label _lblSpkLevel;
    private ProgressBar _barSpk;
    private Button _btnSpkMute;
    private Label _lblTxRate;
    private Label _lblRxRate;
    private Label _lblBuffer;
    private Label _lblClients;
    private Button _btnToggle;
    private System.Windows.Forms.Timer _uiTimer;

    public Form1()
    {
        Text = "WiFi Audio Bridge Server";
        Size = new Size(460, 440);
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        BackColor = Color.FromArgb(240, 240, 245);
        Font = new Font("Segoe UI", 10);

        BuildUI();
        _uiTimer = new System.Windows.Forms.Timer { Interval = 80 };
        _uiTimer.Tick += (_, _) => UpdateUI();
    }

    private void BuildUI()
    {
        int y = 12;
        const int pad = 10;

        var lblTitle = new Label
        {
            Text = "WiFi Audio Bridge",
            Font = new Font("Segoe UI", 18, FontStyle.Bold),
            ForeColor = Color.FromArgb(26, 26, 46),
            Location = new Point(pad, y),
            Size = new Size(420, 36)
        };
        Controls.Add(lblTitle);
        y += 40;

        var statusPanel = CardPanel(pad, y, 420, 50);
        _lblStatus = new Label
        {
            Text = "● Stopped",
            Font = new Font("Segoe UI", 11, FontStyle.Bold),
            ForeColor = Color.Gray,
            Location = new Point(8, 6),
            AutoSize = true
        };
        statusPanel.Controls.Add(_lblStatus);
        _lblIp = new Label
        {
            Text = "",
            Font = new Font("Segoe UI", 9),
            ForeColor = Color.FromArgb(108, 108, 128),
            Location = new Point(8, 28),
            AutoSize = true
        };
        statusPanel.Controls.Add(_lblIp);
        Controls.Add(statusPanel);
        y += 62;

        var micPanel = CardPanel(pad, y, 420, 56);
        micPanel.Controls.Add(new Label
        {
            Text = "🎤  MICROPHONE",
            Font = new Font("Segoe UI", 10, FontStyle.Bold),
            ForeColor = Color.FromArgb(26, 26, 46),
            Location = new Point(8, 6),
            AutoSize = true
        });
        _lblMicLevel = new Label
        {
            Text = "0%",
            Font = new Font("Segoe UI", 9),
            ForeColor = Color.FromArgb(108, 108, 128),
            Location = new Point(160, 6),
            Size = new Size(40, 18),
            TextAlign = ContentAlignment.MiddleRight
        };
        micPanel.Controls.Add(_lblMicLevel);
        _btnMicMute = new Button
        {
            Text = "🔊",
            Font = new Font("Segoe UI", 11),
            Location = new Point(372, 4),
            Size = new Size(40, 22),
            FlatStyle = FlatStyle.Flat,
            BackColor = Color.FromArgb(248, 248, 248),
            FlatAppearance = { BorderColor = Color.FromArgb(200, 200, 200) }
        };
        _btnMicMute.Click += (_, _) =>
        {
            _micMuted = !_micMuted;
            _btnMicMute.Text = _micMuted ? "🔇" : "🔊";
            _btnMicMute.BackColor = _micMuted ? Color.FromArgb(230, 230, 230) : Color.FromArgb(248, 248, 248);
        };
        micPanel.Controls.Add(_btnMicMute);
        _barMic = new ProgressBar
        {
            Location = new Point(8, 28),
            Size = new Size(404, 18),
            Style = ProgressBarStyle.Continuous,
            Maximum = 100,
            Value = 0,
            ForeColor = Color.FromArgb(76, 175, 80),
            BackColor = Color.FromArgb(230, 230, 230)
        };
        micPanel.Controls.Add(_barMic);
        Controls.Add(micPanel);
        y += 64;

        var spkPanel = CardPanel(pad, y, 420, 56);
        spkPanel.Controls.Add(new Label
        {
            Text = "🔊  SPEAKER",
            Font = new Font("Segoe UI", 10, FontStyle.Bold),
            ForeColor = Color.FromArgb(26, 26, 46),
            Location = new Point(8, 6),
            AutoSize = true
        });
        _lblSpkLevel = new Label
        {
            Text = "0%",
            Font = new Font("Segoe UI", 9),
            ForeColor = Color.FromArgb(108, 108, 128),
            Location = new Point(160, 6),
            Size = new Size(40, 18),
            TextAlign = ContentAlignment.MiddleRight
        };
        spkPanel.Controls.Add(_lblSpkLevel);
        _btnSpkMute = new Button
        {
            Text = "🔊",
            Font = new Font("Segoe UI", 11),
            Location = new Point(372, 4),
            Size = new Size(40, 22),
            FlatStyle = FlatStyle.Flat,
            BackColor = Color.FromArgb(248, 248, 248),
            FlatAppearance = { BorderColor = Color.FromArgb(200, 200, 200) }
        };
        _btnSpkMute.Click += (_, _) =>
        {
            _spkMuted = !_spkMuted;
            _btnSpkMute.Text = _spkMuted ? "🔇" : "🔊";
            _btnSpkMute.BackColor = _spkMuted ? Color.FromArgb(230, 230, 230) : Color.FromArgb(248, 248, 248);
        };
        spkPanel.Controls.Add(_btnSpkMute);
        _barSpk = new ProgressBar
        {
            Location = new Point(8, 28),
            Size = new Size(404, 18),
            Style = ProgressBarStyle.Continuous,
            Maximum = 100,
            Value = 0,
            ForeColor = Color.FromArgb(33, 150, 243),
            BackColor = Color.FromArgb(230, 230, 230)
        };
        spkPanel.Controls.Add(_barSpk);
        Controls.Add(spkPanel);
        y += 64;

        var netPanel = CardPanel(pad, y, 420, 90);
        netPanel.Controls.Add(new Label
        {
            Text = "NETWORK",
            Font = new Font("Segoe UI", 8, FontStyle.Bold),
            ForeColor = Color.FromArgb(108, 108, 128),
            Location = new Point(8, 4),
            AutoSize = true
        });

        _lblTxRate = new Label { Text = "Upload:    0 KB/s", Location = new Point(8, 22), AutoSize = true, ForeColor = Color.FromArgb(26, 26, 46) };
        _lblRxRate = new Label { Text = "Download:  0 KB/s", Location = new Point(8, 42), AutoSize = true, ForeColor = Color.FromArgb(26, 26, 46) };
        _lblBuffer = new Label { Text = "Buffer:    0 ms", Location = new Point(8, 62), AutoSize = true, ForeColor = Color.FromArgb(26, 26, 46) };
        _lblClients = new Label
        {
            Text = "Clients: 0",
            Location = new Point(260, 22),
            AutoSize = true,
            ForeColor = Color.FromArgb(108, 108, 128),
            TextAlign = ContentAlignment.MiddleRight
        };
        netPanel.Controls.AddRange(new Control[] { _lblTxRate, _lblRxRate, _lblBuffer, _lblClients });
        Controls.Add(netPanel);
        y += 98;

        _btnToggle = new Button
        {
            Text = "▶  Start Server",
            Font = new Font("Segoe UI", 12, FontStyle.Bold),
            ForeColor = Color.White,
            BackColor = Color.FromArgb(26, 26, 46),
            FlatStyle = FlatStyle.Flat,
            FlatAppearance = { BorderSize = 0 },
            Location = new Point(pad, y),
            Size = new Size(420, 40)
        };
        _btnToggle.Click += (_, _) =>
        {
            if (_running) StopServer();
            else StartServer();
        };
        Controls.Add(_btnToggle);
    }

    private static Panel CardPanel(int x, int y, int w, int h)
        => new() { Location = new Point(x, y), Size = new Size(w, h), BackColor = Color.White };

    public void StartServer()
    {
        if (_running) return;
        _cts = new CancellationTokenSource();
        _running = true;
        Interlocked.Exchange(ref _totalTxBytes, 0);
        Interlocked.Exchange(ref _totalRxBytes, 0);
        _lastTxBytes = 0; _lastRxBytes = 0;
        _lastStatsTime = DateTime.Now;
        _seqSpeaker = 0;
        _hasClient = false;
        _clientConnected = false;
        _clientEp = null;
        lock (_levelLock) { _micLevelSmooth = 0; _spkLevelSmooth = 0; }

        _lblStatus.Text = "● Starting...";
        _lblStatus.ForeColor = Color.FromArgb(255, 152, 0);
        _btnToggle.Text = "■  Stop Server";
        _btnToggle.BackColor = Color.FromArgb(244, 67, 54);

        var localIp = GetLocalIpAddress();
        _lblIp.Text = $"TCP {TcpPort} | UDP {MicUdpPort}/{SpkUdpPort} | {localIp}";

        Task.Run(() => UdpDiscoveryLoop(_cts.Token));
        Task.Run(() => TcpAcceptLoop(_cts.Token));
        _uiTimer.Start();
    }

    public void StopServer()
    {
        _running = false;
        _cts?.Cancel();
        _udpDiscovery?.Close();
        _tcpListener?.Stop();
        _udpMic?.Close();
        _uiTimer.Stop();
        _hasClient = false;
        _clientConnected = false;

        _lblStatus.Text = "● Stopped";
        _lblStatus.ForeColor = Color.Gray;
        _btnToggle.Text = "▶  Start Server";
        _btnToggle.BackColor = Color.FromArgb(26, 26, 46);
        _barMic.Value = 0; _barSpk.Value = 0;
        _lblMicLevel.Text = "0%"; _lblSpkLevel.Text = "0%";
        _lblTxRate.Text = "Upload:    0 KB/s";
        _lblRxRate.Text = "Download:  0 KB/s";
        _lblBuffer.Text = "Buffer:    0 ms";
        _lblClients.Text = "Clients: 0";
    }

    private void UpdateUI()
    {
        lock (_statsLock)
        {
            var now = DateTime.Now;
            var elapsed = (now - _lastStatsTime).TotalSeconds;
            if (elapsed < 0.5) return;

            var txBytes = Interlocked.Read(ref _totalTxBytes);
            var rxBytes = Interlocked.Read(ref _totalRxBytes);
            var txRate = (txBytes - _lastTxBytes) / 1024.0 / elapsed;
            var rxRate = (rxBytes - _lastRxBytes) / 1024.0 / elapsed;
            _lastTxBytes = txBytes; _lastRxBytes = rxBytes;
            _lastStatsTime = now;

            _lblTxRate.Text = $"Upload:    {txRate:F1} KB/s";
            _lblRxRate.Text = $"Download:  {rxRate:F1} KB/s";
        }

        lock (_levelLock)
        {
            var micVal = Math.Max(0, Math.Min(100, (int)Math.Round(_micLevelSmooth)));
            var spkVal = Math.Max(0, Math.Min(100, (int)Math.Round(_spkLevelSmooth)));
            _barMic.Value = Math.Min(micVal, 100);
            _barSpk.Value = Math.Min(spkVal, 100);
            _lblMicLevel.Text = $"{micVal}%";
            _lblSpkLevel.Text = $"{spkVal}%";
        }
    }

    private void UpdateMicLevel(float rawLevel)
    {
        lock (_levelLock) { _micLevelSmooth = _micLevelSmooth * 0.5f + rawLevel * 0.5f; }
    }

    private void UpdateSpkLevel(float rawLevel)
    {
        lock (_levelLock) { _spkLevelSmooth = _spkLevelSmooth * 0.5f + rawLevel * 0.5f; }
    }

    private void UdpDiscoveryLoop(CancellationToken ct)
    {
        _udpDiscovery = new UdpClient(UdpPort);
        var ep = new IPEndPoint(IPAddress.Any, 0);

        while (!ct.IsCancellationRequested)
        {
            try
            {
                var data = _udpDiscovery.Receive(ref ep);
                var msg = Encoding.UTF8.GetString(data).Trim();
                if (msg == "WIFI_AUDIO_WHO?")
                {
                    var localIp = GetLocalIpAddress();
                    var resp = Encoding.UTF8.GetBytes($"WIFI_AUDIO_HERE:{localIp}");
                    _udpDiscovery.Send(resp, resp.Length, ep);
                    BeginInvoke(() => _lblStatus.Text = $"● Request from {ep.Address}");
                }
            }
            catch when (!_running) { break; }
        }
    }

    private void TcpAcceptLoop(CancellationToken ct)
    {
        _tcpListener = new TcpListener(IPAddress.Any, TcpPort);
        _tcpListener.Start();

        while (!ct.IsCancellationRequested)
        {
            try
            {
                var client = _tcpListener.AcceptTcpClient();
                lock (_clientLock)
                {
                    if (_hasClient) { client.Close(); continue; }
                    _hasClient = true;
                }
                BeginInvoke(() => { _lblClients.Text = "Clients: 1"; });
                Task.Run(() => HandleClient(client), ct);
            }
            catch when (!_running) { break; }
        }
    }

    private void HandleClient(TcpClient client)
    {
        var clientIp = ((IPEndPoint?)client.Client?.RemoteEndPoint)?.Address;
        if (clientIp == null) { client.Close(); _hasClient = false; return; }

        _lastBufferUpdate = DateTime.MinValue;
        _seqSpeaker = 0;

        using var netStream = client.GetStream();

        try
        {
            var buf = new byte[64];
            netStream.ReadTimeout = 5000;
            var headerBytes = ReadSome(netStream, buf, 0, 4);
            if (headerBytes < 4 || Encoding.UTF8.GetString(buf, 0, 4) != "UDP\n")
            {
                netStream.Write("NO\n"u8); client.Close();
                _hasClient = false; return;
            }

            var waveFormat = new WaveFormat(SampleRate, 16, 1);
            var waveIn = new WaveInEvent
            {
                BufferMilliseconds = 20,
                WaveFormat = waveFormat
            };

            _waveProvider = new BufferedWaveProvider(waveFormat)
            {
                BufferDuration = TimeSpan.FromMilliseconds(40),
                DiscardOnBufferOverflow = true
            };
            var waveOut = new WasapiOut(AudioClientShareMode.Shared, 20);
            waveOut.Init(_waveProvider);
            waveOut.Play();

            _udpMic = new UdpClient(MicUdpPort);
            _udpMic.Client.ReceiveTimeout = 1000;
            var clientEp = new IPEndPoint(clientIp, SpkUdpPort);
            var ct = _cts?.Token ?? CancellationToken.None;

            waveIn.DataAvailable += (_, e) =>
            {
                if (!_running || _micMuted) return;
                var rms = CalculateRms(e.Buffer, 0, e.BytesRecorded);
                BeginInvoke(() => UpdateMicLevel((float)(rms / 32768.0 * 100)));

                try
                {
                    var hdr = new byte[HeaderSize];
                    EncodeHeader(hdr, _seqSpeaker++, 1, e.BytesRecorded);
                    var pkt = new byte[HeaderSize + e.BytesRecorded];
                    Buffer.BlockCopy(hdr, 0, pkt, 0, HeaderSize);
                    Buffer.BlockCopy(e.Buffer, 0, pkt, HeaderSize, e.BytesRecorded);
                    _udpMic?.Send(pkt, pkt.Length, clientEp);
                    Interlocked.Add(ref _totalTxBytes, pkt.Length);
                }
                catch { }
            };
            waveIn.StartRecording();

            netStream.Write("OK\n"u8);

            BeginInvoke(() =>
            {
                _lblStatus.Text = "● Streaming (UDP)";
                _lblStatus.ForeColor = Color.FromArgb(76, 175, 80);
                _clientConnected = true;
            });

            var udpTask = Task.Run(() => UdpMicReceiveLoop(_udpMic, ct), ct);

            var monitorBuf = new byte[64];
            while (_running && !ct.IsCancellationRequested)
            {
                try
                {
                    int n = netStream.Read(monitorBuf, 0, monitorBuf.Length);
                    if (n == 0) break;
                }
                catch (IOException ex) when (ex.InnerException is SocketException { SocketErrorCode: SocketError.TimedOut })
                {
                    continue;
                }
                catch (IOException)
                {
                    break;
                }
                catch { break; }
            }

            waveIn.StopRecording();
            waveOut.Stop();
            udpTask.Wait(1000);
            _udpMic?.Close();
            _udpMic = null;
            waveIn.Dispose();
            waveOut.Dispose();
        }
        catch { }
        finally
        {
            client.Close();
            _hasClient = false;
            _clientConnected = false;
            _seqSpeaker = 0;
            BeginInvoke(() =>
            {
                _lblClients.Text = "Clients: 0";
                _lblStatus.Text = "● Waiting for client...";
                _lblStatus.ForeColor = Color.FromArgb(255, 152, 0);
                _barMic.Value = 0; _barSpk.Value = 0;
                _lblMicLevel.Text = "0%"; _lblSpkLevel.Text = "0%";
                _lblBuffer.Text = "Buffer: 0 ms";
            });
        }
    }

    private void UdpMicReceiveLoop(UdpClient udp, CancellationToken ct)
    {
        var ep = new IPEndPoint(IPAddress.Any, 0);
        var waveProvider = _waveProvider;

        while (!ct.IsCancellationRequested && _running && _clientConnected)
        {
            try
            {
                var data = udp.Receive(ref ep);
                if (data.Length < HeaderSize) continue;

                var magic = data[0] | (data[1] << 8);
                if (magic != Magic) continue;

                var payloadLen = data[8] | (data[9] << 8) | (data[10] << 16) | (data[11] << 24);
                if (payloadLen < 1 || payloadLen > data.Length - HeaderSize) continue;
                if (data[12] != 0) continue;

                Interlocked.Add(ref _totalRxBytes, data.Length);

                var rms = CalculateRms(data, HeaderSize, payloadLen);
                var level = (float)(rms / 32768.0 * 100);
                BeginInvoke(() => UpdateSpkLevel(level));

                if (!_spkMuted && waveProvider != null)
                    waveProvider.AddSamples(data, HeaderSize, payloadLen);

                var now = DateTime.UtcNow;
                if ((now - _lastBufferUpdate).TotalMilliseconds >= 200)
                {
                    _lastBufferUpdate = now;
                    BeginInvoke(() =>
                    {
                        _lblBuffer.Text = $"Buffer: {waveProvider?.BufferedDuration.TotalMilliseconds:F0} ms";
                    });
                }
            }
            catch (SocketException) { continue; }
            catch when (!_running) { break; }
        }
    }

    private static int ReadSome(NetworkStream stream, byte[] buf, int off, int count)
    {
        int total = 0;
        while (total < count)
        {
            int n = stream.Read(buf, off + total, count - total);
            if (n == 0) break;
            total += n;
        }
        return total;
    }

    private static void EncodeHeader(byte[] hdr, ushort seq, byte type, int pcmLen)
    {
        hdr[0] = (byte)(Magic & 0xFF);
        hdr[1] = (byte)((Magic >> 8) & 0xFF);
        hdr[2] = (byte)((seq >> 8) & 0xFF);
        hdr[3] = (byte)(seq & 0xFF);
        hdr[4] = 0; hdr[5] = 0; hdr[6] = 0; hdr[7] = 0;
        hdr[8] = (byte)(pcmLen & 0xFF);
        hdr[9] = (byte)((pcmLen >> 8) & 0xFF);
        hdr[10] = (byte)((pcmLen >> 16) & 0xFF);
        hdr[11] = (byte)((pcmLen >> 24) & 0xFF);
        hdr[12] = type;
    }

    private static double CalculateRms(byte[] buf, int off, int bytes)
    {
        var samples = bytes / 2; if (samples == 0) return 0;
        long sumSq = 0;
        for (int i = off; i < off + bytes - 1; i += 2)
        {
            var s = (short)(buf[i] | (buf[i + 1] << 8));
            sumSq += s * s;
        }
        return Math.Sqrt((double)sumSq / samples);
    }

    private static string GetLocalIpAddress()
    {
        var best = "127.0.0.1"; var bestScore = -1;
        foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (ni.OperationalStatus != OperationalStatus.Up) continue;
            if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
            var desc = ni.Description.ToLowerInvariant();
            int score;
            if (desc.Contains("virtual") || desc.Contains("hyper-v") ||
                desc.Contains("vmware") || desc.Contains("virtualbox") ||
                desc.Contains("docker") || desc.Contains("pseudo") ||
                desc.Contains("bluetooth") || desc.Contains(" tunneling") ||
                ni.NetworkInterfaceType == NetworkInterfaceType.Tunnel ||
                ni.NetworkInterfaceType == NetworkInterfaceType.Slip ||
                ni.NetworkInterfaceType == NetworkInterfaceType.Ppp)
                score = 0;
            else if (ni.NetworkInterfaceType == NetworkInterfaceType.Wireless80211)
                score = 10;
            else if (ni.NetworkInterfaceType == NetworkInterfaceType.Ethernet)
                score = 8;
            else score = 2;
            if (score <= bestScore) continue;
            var addr = ni.GetIPProperties().UnicastAddresses
                .FirstOrDefault(a => a.Address.AddressFamily == AddressFamily.InterNetwork
                                     && !IPAddress.IsLoopback(a.Address));
            if (addr == null) continue;
            bestScore = score; best = addr.Address.ToString();
        }
        return best;
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        StopServer();
        base.OnFormClosing(e);
    }
}
