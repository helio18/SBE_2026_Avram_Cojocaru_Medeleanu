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
    --publications=10000 --rate=50 --duration-seconds=180 \
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

### Comparatie: implementare default (text) vs bonus (protobuf)

Publisher-ul accepta `--transport=text|protobuf` (implicit `protobuf`). Modul `text` foloseste calea de dinainte de bonus: `PUB` serializat text linie-cu-linie, trimis pe portul text al brokerului (acelasi pe care brokerul deja trateaza `PUB`-urile forwardate). Modul `protobuf` foloseste transportul binar dedicat de mai sus. Astfel se pot rula si compara ambele variante pe acelasi scenariu.

Comparatia se ruleaza cu:

```powershell
powershell -ExecutionPolicy Bypass -File Project\compare-transport.ps1
```

Rezultate (acelasi scenariu nesaturat: company-equals=100, 10000 subscriptii, 3 subscriberi, 3 brokeri, rata 50 pub/s, feed 60 s, i7-12650H):

| Metrica | Text (fara bonus) | Protobuf (bonus) |
| --- | --- | --- |
| Publicatii emise | 3000 | 3000 |
| Octeti / publicatie (medie) | 75.99 B | **65.76 B** |
| Total octeti emisi | 227 962 | 197 288 |
| Notificari livrate | 3 750 314 | 3 750 314 |
| Latenta medie de livrare | 921.067 ms | **318.166 ms** |

- **Echivalenta functionala**: ambele variante livreaza exact acelasi numar de notificari (3 750 314), deci protobuf nu schimba semantica, doar reprezentarea pe sarma.
- **Dimensiune payload**: protobuf reduce cu **~13.5%** octetii per publicatie (codare binara compacta vs text cu zecimale formatate `%.6f`).
- **Latenta**: in aceasta rulare protobuf a livrat cu latenta mai mica, in parte pentru ca publicatiile intra pe un port binar dedicat, separat de `LineServer`-ul care duce notificarile; valorile absolute de latenta sunt sensibile la incarcarea masinii.

## Rezultate evaluare

Rulare conform cerintei: 3 minute feed (180 s), 10000 subscriptii, 3 subscriberi, 1 publisher, procesor 12th Gen Intel Core i7-12650H, OpenJDK 17.0.14. Raportul live se genereaza local sub `Project/output/eval.*/final-report.md` (folderul `output/` este gitignored, deci nu este versionat).

### (a) + (b) Livrare si latenta (sistem live, scenariu nesaturat)

Masurate pe scenariu A (100% `=`), unde volumul de notificari ramane in limita debitului sistemului:

| Metrica | Valoare |
| --- | --- |
| Subscriptii inregistrate | 10000 (round-robin 3334 / 3333 / 3333 pe brokeri) |
| Publicatii emise in 3 min (rata 50/s) | 9000 |
| Publicatii intrate efectiv in retea (binar) | 9000 (3000 / broker direct + 6000 forwardate de la vecini) |
| Notificari livrate cu succes | 11 251 130 |
| Latenta medie de livrare | **25.303 ms** |

### (c) Rata de matching: 100% `=` vs 25% `=`

Rata de matching este o proprietate semantica a subscriptiilor si a publicatiilor, deci se masoara pe setul de date generat (10000 subscriptii pe campul `company` x 5000 publicatii = 50 000 000 perechi evaluate cu `MatchingEngine`), independent de eventuala saturare a livrarii:

| Scenariu | Rata masurata | Rata teoretica |
| --- | --- | --- |
| A (100% `=`) | **12.4924%** | 12.5% (1/8) |
| B (25% `=`) | **68.7519%** | 68.75% (0.25 * 1/8 + 0.75 * 7/8) |

Cu 8 valori posibile pentru `company`: o subscriptie cu `=` matcheaza 1/8 din publicatii; o subscriptie cu `!=` matcheaza 7/8. La 100% `=` rata este 12.5%; la 25% `=` cele 75% de subscriptii cu `!=` ridica rata la ~68.75%. Masurarea live de pe scenariu A (12.5007% in raportul generat) confirma aceeasi valoare, validand empiric motorul de matching.

### Observatie privind debitul scenariului B

In rularea live, scenariu B satureaza calea de livrare pe aceasta masina: cele ~75% subscriptii cu `!=` genereaza ~68.75% * 10000 = ~6875 notificari per publicatie, adica peste 100k notificari/s catre subscriberi prin TCP loopback, mai mult decat poate prelucra `LineServer`-ul single-thread de pe subscriber. Coada de livrare creste (latenta de ordinul zecilor de secunde) atat la 50 pub/s cat si la 25 pub/s. De aceea (a) si (b) se raporteaza pe scenariu A (nesaturat), iar (c) pe setul de date (livrare-agnostic). Cresterea debitului de livrare (de ex. pool de thread-uri pentru notificari) ar permite si masurarea live nesaturata a scenariului B, dar nu este necesara pentru cerintele temei.

Stats per broker (vezi `Project/output/eval.*/scenario-A-eq-100/B*.stats`) arata distributia balansata a subscriptiilor (round-robin: 3334 / 3333 / 3333) si rutarea publicatiilor prin overlay (fiecare broker primeste atat publicatii direct de la publisher prin transport binar, cat si forwardate de la vecini).
