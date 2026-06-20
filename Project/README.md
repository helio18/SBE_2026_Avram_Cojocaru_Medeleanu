# Proiect SBE 2026 - sistem publish/subscribe content-based

Acest proiect implementeaza arhitectura ceruta de proiectul de final pe baza generatorului din `Homework/`. Codul de generare nu este duplicat - `Project/` foloseste clasele din `Homework/` prin classpath.

## Componente

| Tip nod | Cati | Rol |
| --- | --- | --- |
| Publisher | 1 (configurabil pana la 2) | Genereaza publicatii cu `DatasetGenerator` si le emite catre brokeri |
| Broker | 3 | Formeaza un overlay in topologie triunghi (B1-B2-B3-B1), stocheaza subscriptii local si forwardeaza publicatii pe baza unui routing table |
| Subscriber | 3 (configurabil) | Genereaza subscriptii cu `DatasetGenerator` si le distribuie balansat pe brokeri, asculta notificarile |

Toate componentele ruleaza ca procese Java separate si comunica prin TCP sockets (line-based, mesaje text delimitate cu `|`).

## Algoritm de rutare (advertisement-based)

1. La `SUB` primit de la subscriber:
   - brokerul stocheaza subscriptia local impreuna cu `host:port`-ul subscriberului;
   - trimite un `ADV` catre fiecare broker vecin cu acelasi continut.
2. La `ADV` primit de la un broker vecin:
   - brokerul retine in `RoutingTable` ce predicate sunt accesibile prin acel vecin;
   - duplicatele (acelasi `subId@origin`) sunt ignorate.
3. La `PUB` primit (de la publisher sau de la un broker vecin):
   - daca id-ul publicatiei a mai fost vazut, se ignora (anti-bucla);
   - brokerul matcheaza local toate subscriptiile sale si trimite cate o `NOT` la fiecare subscriber care matcheaza;
   - brokerul forwardeaza publicatia doar catre vecinii care au cel putin un advertisement potrivit (rutare selectiva, nu flooding).

Astfel:
- subscriptiile unui subscriber sunt distribuite **balansat** prin round-robin pe brokeri;
- publicatiile **trec prin mai multi brokeri** pana la subscriberi, fiecare broker contribuind la rutare;
- nu exista un broker central care sa stocheze toate subscriptiile.

## Balansare round-robin

Fiecare subscriber tine un contor `i` si trimite subscriptia `i` catre `brokers[i % numarBrokeri]`. Pentru 10000 subscriptii pe 3 brokeri rezulta o distributie 3334 / 3333 / 3333 (in functie de cati subscriberi exista, distributia se balanseaza si intre subscriberi).

## Cum se compileaza

```bash
bash Project/build.bash
```

Pe Windows / PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File Project\build.ps1
```

Compileaza `Homework/src` (in `Homework/bin`) si apoi `Project/src` (in `Project/bin`), reutilizand clasele `homework.*` prin classpath.

## Cum se ruleaza evaluarea

```bash
bash Project/run-eval.bash
```

Pe Windows / PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File Project\run-eval.ps1
```

Pasi:
1. compileaza sursele;
2. pentru fiecare din cele doua scenarii (`scenario-A-eq-100` cu `company_equals=100%` si `scenario-B-eq-25` cu `company_equals=25%`):
   - porneste 3 brokeri (porturi 5001, 5002, 5003);
   - porneste 3 subscriberi (porturi 6001, 6002, 6003), fiecare cu portia sa din cele 10000 subscriptii;
   - porneste 1 publisher care emite publicatii la `PUBLICATION_RATE` per secunda timp de `DURATION_SECONDS` secunde;
   - la final scrie un stop file, toate procesele se opresc curat si salveaza statistici;
3. agregheaza statisticile per scenariu si scrie raportul final in `Project/output/eval.XXXXXX/final-report.md`.

### Parametri configurabili (variabile de mediu)

| Variabila | Default | Descriere |
| --- | --- | --- |
| `DURATION_SECONDS` | `180` | Durata feed-ului de publicatii per scenariu |
| `TOTAL_SUBSCRIPTIONS` | `10000` | Numar total de subscriptii inregistrate per scenariu |
| `PUBLICATION_RATE` | `50` | Publicatii pe secunda emise de fiecare publisher |
| `PUBLISHER_COUNT` | `1` | Numar de publisheri |
| `SUBSCRIBER_COUNT` | `3` | Numar de subscriberi |
| `SUB_SEND_GRACE_SECONDS` | `8` | Cat timp se asteapta dupa lansarea subscriberilor pentru a se inregistra |
| `DRAIN_GRACE_SECONDS` | `5` | Cat timp se mai asteapta dupa ce publisherii au terminat pentru livrari intarziate |

Exemple:

```bash
# rulare rapida pentru smoke test
DURATION_SECONDS=10 TOTAL_SUBSCRIPTIONS=300 PUBLICATION_RATE=20 bash Project/run-eval.bash

# rulare conform cerintei (3 min, 10k subscriptii)
bash Project/run-eval.bash
```

Echivalent pe Windows / PowerShell:

```powershell
$env:DURATION_SECONDS=10
$env:TOTAL_SUBSCRIPTIONS=300
$env:PUBLICATION_RATE=20
powershell -ExecutionPolicy Bypass -File Project\run-eval.ps1
```

### Cum se ruleaza componentele manual

Pentru debug sau experimente, componentele se pot porni si individual.

Notă: clasele generate pentru protobuf depind de `protobuf-java-4.28.3.jar`, care trebuie inclus pe classpath atât la `javac` cât și la `java`.

Broker:

```bash
java -cp "Homework/bin;Project/bin;Project/lib/protobuf-java-4.28.3.jar" project.broker.BrokerMain \
    --id=B1 --port=5001 --pub-port=7001 \
    --peers=B2@localhost:5002,B3@localhost:5003 \
    --stop-file=/tmp/STOP \
    --stats-file=/tmp/B1.stats
```

Subscriber (folosește porturile text pentru SUB):

```bash
java -cp "Homework/bin;Project/bin;Project/lib/protobuf-java-4.28.3.jar" project.subscriber.SubscriberMain \
    --id=S1 --listen-port=6001 \
    --brokers=B1@localhost:5001,B2@localhost:5002,B3@localhost:5003 \
    --subscriptions=3334 --company-equals=100 \
    --stop-file=/tmp/STOP \
    --stats-file=/tmp/S1.stats
```

Publisher (folosește pub-porturile binare):

```bash
java -cp "Homework/bin;Project/bin;Project/lib/protobuf-java-4.28.3.jar" project.publisher.PublisherMain \
    --id=P1 \
    --brokers=B1@localhost:7001,B2@localhost:7002,B3@localhost:7003 \
    --publications=10000 --rate=25 --duration-seconds=180 \
    --stats-file=/tmp/P1.stats
```

Pe Windows, foloseste scripturile `build.ps1` si `run-eval.ps1`. Pentru rulare manuala, inlocuieste path-urile Linux precum `/tmp/...` cu un path local, de exemplu `Project/output/tmp/...`.

## Structura codului

```
Project/src/project/
  ProjectApp.java                 - smoke test inline pentru MatchingEngine
  matching/MatchingEngine.java    - filtrare content-based (Publication + Subscription)
  routing/RoutingTable.java       - per neighbor: predicate advertisate
  transport/
    MessageCodec.java             - serializare/parsing mesaje SUB|ADV|PUB|NOT (text)
    LineServer.java               - server TCP linie cu linie
    OutboundConnections.java      - cache de conexiuni TCP outbound (cu reconectare)
    BinaryPubServer.java          - server TCP pentru publicatii protobuf (bonus 1)
    BinaryPubClient.java          - client TCP pentru publicatii protobuf (bonus 1)
    Args.java                     - parser de argumente CLI + waitForStopFile
  broker/
    Broker.java                   - logica brokerului
    BrokerMain.java               - CLI entry
  publisher/PublisherMain.java    - CLI entry
  subscriber/SubscriberMain.java  - CLI entry

Project/src-gen/project/proto/
  Pubsub.java                     - cod Java generat din publication.proto

Project/proto/publication.proto   - schema protobuf
Project/lib/protobuf-java-*.jar   - runtime protobuf
```

Modelul (`Publication`, `Subscription`, `SubscriptionCondition`) este reutilizat direct din `Homework/src/homework/` prin classpath, fara wrappere proprii.

## Format mesaje

Toate mesajele sunt linie text terminate cu `\n`, campuri separate prin `|`.

| Tip | Format |
| --- | --- |
| `SUB` | `SUB\|<subId>\|<subscriberHost>\|<subscriberPort>\|<conditiiCodificate>` |
| `ADV` | `ADV\|<subId>\|<originBroker>\|<hopCount>\|<conditiiCodificate>` |
| `PUB` | `PUB\|<pubId>\|<emitTsMs>\|<hopCount>\|<company>\|<value>\|<drop>\|<variation>\|<date>` (numai forwarding broker-broker) |
| `NOT` | `NOT\|<pubId>\|<emitTsMs>\|<subId>\|<company>\|<value>\|<drop>\|<variation>\|<date>` |

Conditiile sunt codificate cu acelasi token ca in fisierele Homework (`(field,op,value);(field,op,value);...`).

## Bonus 1 - serializare binara Protocol Buffers

Publicatiile emise de publisher catre brokeri sunt serializate cu **Google Protocol Buffers** (`protobuf-java 4.28.3`), nu in formatul text linie-cu-linie. Schema (`Project/proto/publication.proto`):

```proto
message Publication {
  string pub_id = 1;
  int64 emit_timestamp_ms = 2;
  int32 hop_count = 3;
  string company = 4;
  double value = 5;
  double drop = 6;
  double variation = 7;
  string date = 8;
}
```

Codul Java generat de `protoc` este in `Project/src-gen/project/proto/Pubsub.java` (committed pentru a evita dependenta de `protoc` la build).
Pentru regenerare, cu `protoc` 28.3 instalat: `protoc --proto_path=Project/proto --java_out=Project/src-gen Project/proto/publication.proto`.

**Transport binar dedicat**: fiecare broker asculta pe doua porturi:
- portul text (`--port=5001`) pentru `SUB`, `ADV`, `NOT` si pentru forwarding-ul `PUB` intre brokeri;
- portul binar (`--pub-port=7001`) pentru `PUB` venit de la publisher, framing length-delimited (`Publication.writeDelimitedTo` / `parseDelimitedFrom`).

Forwarding-ul `PUB` intre brokeri ramane in format text, asa cum cere cerinta bonus ("publicatiile publisher -> brokers"). Dupa deserializare, brokerul construieste mesaj text si propaga prin acelasi pipeline ca inainte.

**Fisiere noi/atinse**:
- `Project/lib/protobuf-java-4.28.3.jar` - biblioteca runtime (in classpath pentru `javac` si `java`);
- `Project/proto/publication.proto` - schema;
- `Project/src-gen/project/proto/Pubsub.java` - cod Java generat;
- `Project/src/project/transport/BinaryPubServer.java`, `BinaryPubClient.java` - transport binar TCP cu framing length-delimited;
- `Broker` accepta `--pub-port` si numara `publicationsReceivedBinary` in fisierul de statistici.

## Rezultate evaluare

Rulare completa conform cerintei: 3 minute feed, 10000 subscriptii, 3 subscriberi, 1 publisher la rata 25 pub/s, procesor Intel Core i5-10400. Raport detaliat: [output/eval.zmWV8h/final-report.md](output/eval.zmWV8h/final-report.md).

| Metrica | Scenariu A (100% `=`) | Scenariu B (25% `=`) |
| --- | --- | --- |
| Subscriptii inregistrate | 10000 | 10000 |
| Publicatii emise in 3 minute | 4500 | 4500 |
| Notificari livrate cu succes | 5 625 331 | 30 930 163 |
| Latenta medie de livrare | **5.269 ms** | **24.240 ms** |
| Rata de matching | **12.5007%** | **68.7337%** |
| Rata de matching teoretica | 12.5% (1/8) | 68.75% (0.25 * 1/8 + 0.75 * 7/8) |
| Eroare fata de teorie | 0.005 puncte procentuale | 0.02 puncte procentuale |

Concluzia comparativa pentru cerinta (c):

- Cu 8 valori posibile pentru `company`, scenariu A (100% `=`) matcheaza in medie 1/8 din publicatii per subscriptie.
- Scenariu B (25% `=`) are 75% din subscriptii cu `!=` care matcheaza 7/8, deci rata totala creste la ~68.75%.
- Scenariu B **livreaza ~5.5x mai multe notificari** decat scenariu A pentru aceeasi rata de publicatii.
- Latenta in scenariu B creste de la 5 ms la 24 ms din cauza saturatiei TCP loopback la ~170k notificari/secunda; sistemul ramane functional fara pierderi.

Stats per broker din rularea de referinta (vezi `Project/output/eval.zmWV8h/scenario-A-eq-100/B*.stats`) arata distributia balansata a subscriptiilor (round-robin: ~3334 / 3333 / 3333) si rutarea publicatiilor prin overlay (fiecare broker primeste atat publicatii direct de la publisher, cat si forwardate de la vecini).
