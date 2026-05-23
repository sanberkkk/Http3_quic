# Bonus (Kısım II) – Kanıt Özeti

**Tarih:** 2026-05-23  
**Teknoloji:** quiche4j (Java) + JNI `libquiche_jni.dylib`  
**Sunucu:** `io.quiche4j.examples.Http3Server` (UDP 4433)  
**İstemci:** `io.quiche4j.examples.Http3Client` → `https://127.0.0.1:4433/`

## Başarılı koşu çıktısı (istemci)

```
! h3 conn is established
:status: 200
server: Quiche4j
content-length: 11
Hello world
> response finished
```

Bu, **gerçek TLS + HTTP/3 GET** kanıtıdır (Kısım I'deki manuel header simülasyonundan farklı).

## JNI kurulumu (bir kez)

```bash
brew install rust cmake
chmod +x bonus/setup-jni.sh
./bonus/setup-jni.sh
export QUICHE_JNI_LIB=/tmp/quiche4j-native/release
```

## Çalıştırma (iki terminal)

**Terminal 1 – sunucu:**
```bash
cd /Users/sanberk96/Projects/network
export SSLKEYLOGFILE=logs/sslkeys-bonus.log
./bonus/run-server.sh 4433
```

**Terminal 2 – istemci:**
```bash
cd /Users/sanberk96/Projects/network
export SSLKEYLOGFILE=logs/sslkeys-bonus.log
./bonus/run-client.sh https://127.0.0.1:4433/
```

Konsol kopyası: `logs/bonus-client-console.txt`

## Wireshark (şifre çözülmüş QUIC)

1. Sunucu + istemci çalışırken **lo0** veya **Any** üzerinde capture
2. Filtre: `udp.port == 4433`
3. Preferences → Protocols → TLS → (Pre)-Master-Secret log: `logs/sslkeys-bonus.log`
4. Bir pakette **Decrypted QUIC** / HTTP/3 başlıkları (`:method: GET`, `:status: 200`)
5. ALPN: `h3-29` veya `h3`

**Not:** `enableLogKeys()` `SSLKEYLOGFILE` ayarlıyken etkinleştirilir (`BonusTlsConfig`). Dosya boşsa Wireshark yine QUIC header seviyesinde analiz yapılabilir; tam decrypt için log dosyasının dolu olduğunu doğrula.

## Raporda vurgulanacak karşılaştırma

| | Kısım I (`QuicheClient`) | Bonus (quiche4j) |
|--|--------------------------|------------------|
| API | Manuel UDP + sabit header | `Quiche.connect` + `Http3Connection` |
| TLS | Yok | TLS 1.3 (QUIC içinde) |
| HTTP/3 | Yok | GET + `200` + body |
| Wireshark | Initial paketleri | Handshake + şifre çözme (key log ile) |

## Sunucuyu durdurma

Bonus sunucu arka planda çalışıyorsa: `pkill -f Http3Server` veya ilgili terminalde Ctrl+C.
