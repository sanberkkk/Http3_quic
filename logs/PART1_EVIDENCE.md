# Kısım I – Kanıt Toplama Özeti

**Tarih:** 2026-05-23  
**Sunucu:** Docker `quiche-server` (Rust `examples/server`, UDP 4433)  
**İstemci:** `part1/target/part1-1.0-SNAPSHOT.jar` (`QuicheClient`)

## 1. Sunucu (Docker)

```bash
docker rm -f quiche-server 2>/dev/null || true
docker run -d --name quiche-server -w /quiche/quiche \
  -p 4433:4433/udp quiche-proje \
  /quiche/target/debug/examples/server
```

Durum kontrolü: `docker ps --filter name=quiche-server`

## 2. İstemci ve loglar

```bash
cd /Users/sanberk96/Projects/network
SSLKEYLOGFILE=logs/sslkeys.log java -jar part1/target/part1-1.0-SNAPSHOT.jar
```

### Konsol sonuçları (örnek koşu)

| Metrik | Değer |
|--------|-------|
| Start Event Count | 10 |
| Stop Event Count | 10 |
| Fail Event Count | 0 |
| TPS (konsol) | ~121,95 |
| TPS (tracker) | ~80,90 tx/sn |
| Başarı oranı | 100% |
| Test penceresi | ~0,124 s |

### Log dosyası

- **Yol:** `quic_project_logs.txt` (proje kökü)
- **Format:** `[yyyy-MM-dd HH:mm:ss.SSS] [thread#id] [Event: START|STOP|FAIL] - mesaj`
- **Satır sayısı:** 10 START + 10 STOP (son koşu)

Kopya: `logs/part1-run-console.txt`

## 3. Wireshark – yerel senaryo (127.0.0.1:4433)

1. Arayüz: **Loopback** veya **Any** (macOS: `lo0` + `en0` birlikte de seçilebilir)
2. Filtre: `udp.port == 4433`
3. İstemciyi çalıştır → **10 paket** (NUM_EVENT=10)
4. Ekran görüntüsü için bir paketi aç:
   - **Protocol:** QUIC
   - **Packet Type:** Initial
   - **Version:** 0x00000001
   - **Destination Connection ID:** `aa:aa:...` (8 byte)
   - **Source Connection ID:** `bb:bb:...` (8 byte)

**Rapor cümlesi:** Multithread Java istemci, `DatagramChannel` ile QUIC Initial formatında UDP paketleri üretmiş; Wireshark ile 10 gönderim doğrulanmıştır.

## 4. Wireshark – dış HTTP/3 sitesi (PDF zorunluluğu)

macOS sistem `curl` HTTP/3 desteklemiyor (`curl: option --http3: the installed libcurl version doesn't support this`).

### Seçenek A – Docker quiche HTTP/3 istemcisi (önerilen)

Wireshark’ı **Wi‑Fi / Ethernet** arayüzünde başlat (loopback değil), sonra:

```bash
docker run --rm --network host quiche-proje \
  /quiche/target/debug/examples/http3-client https://cloudflare-quic.com/
```

Alternatif: `https://quic.tech:8443/`

Filtre: `udp.port == 443 or udp.port == 8443 or quic`

Ekran görüntüsü: Initial + Handshake, ALPN `h3`, sunucu cevabı.

### Seçenek B – Tarayıcı

Chrome/Edge/ Firefox (QUIC etkin) → `https://cloudflare-quic.com`  
Wireshark’ta aynı arayüzde UDP 443 trafiği.

### Seçenek C – Homebrew curl (HTTP/3)

```bash
brew install curl
/opt/homebrew/opt/curl/bin/curl --http3 -I https://cloudflare-quic.com/
```

## 5. SSLKEYLOGFILE (isteğe bağlı şifre çözme)

```bash
export SSLKEYLOGFILE=/Users/sanberk96/Projects/network/logs/sslkeys.log
```

Wireshark → Preferences → Protocols → TLS → (Pre)-Master-Secret log filename.

## 6. Rapor için dürüst varsayım

Kısım I Java kodu, PDF’deki multithread + nio + loglama + TPS gereksinimlerini karşılar. QUIC paket gönderimi **simüle edilmiş Initial header** ile yapılır; tam HTTP/3 handshake Kısım I istemcisinde yoktur. Sunucu tarafı Docker’daki **Rust quiche** örneği ile sağlanır. Dış site trafiği ayrı bir kanıt oturumu olarak Wireshark’a eklenmelidir.
