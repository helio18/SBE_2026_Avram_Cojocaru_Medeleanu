# EXPLICAȚII — cele 3 bonusuri

Acest document explică în detaliu cum au fost implementate cele trei bonusuri, ce
s-a adăugat față de proiectul de bază, fluxul de execuție, structurile de date
folosite și unde anume în cod se află fiecare lucru. Este scris ca să poți răspunde
la întrebările profesorului fără să recitești tot codul.

---

## 0. Context: arhitectura de bază (ca să înțelegi unde se grefează bonusurile)

Sistemul este un **publish/subscribe content-based** cu o rețea (overlay) de 3 brokeri
legați în triunghi (B1–B2–B3). Fiecare broker ascultă pe **două** porturi:

- un port **text** (5001/5002/5003) pe care primește: `SUB` (subscripții de la
  subscriberi), `ADV` (advertisement-uri de la ceilalți brokeri) și `PUB` (publicații
  forwardate între brokeri) — vezi `LineServer`;
- un port **binar/protobuf** (7001/7002/7003) pe care primește publicațiile direct de
  la publisher — vezi `BinaryPubServer` (ăsta este bonusul 1).

Rutarea este **bazată pe advertisement** (rutare selectivă, nu flooding):

1. Subscriberul trimite `SUB` către un broker → `Broker.handleSubscribe()`.
2. Brokerul memorează subscripția local (`localSubscriptions`) și trimite un `ADV`
   către toți vecinii.
3. Vecinii memorează „pe direcția vecinului X există interes pentru subscripția Y” în
   `RoutingTable` (`Broker.handleAdvertisement()`).
4. Când vine o publicație, brokerul: (a) o livrează subscriberilor lui locali care fac
   match și (b) o forwardează **doar** către vecinii despre care știe, din tabela de
   rutare, că ar putea fi interesați (`Broker.processPublication()`).

Matching-ul efectiv (publicație vs. condiții de subscripție) se face în
`MatchingEngine.matches()`. Acest detaliu este important pentru bonusul 3.

### Fișiere pe care TREBUIE să le cunoști vs. ce este „doar librărie”

| Categorie | Fișiere |
| --- | --- |
| **Cod scris de noi (trebuie cunoscut)** | `Broker.java`, `BrokerMain.java`, `PublisherMain.java`, `SubscriberMain.java`, `MatchingEngine.java`, `RoutingTable.java`, `MessageCodec.java`, `LineServer.java`, `OutboundConnections.java`, `BinaryPubClient.java`, `BinaryPubServer.java`, `MessageCrypto.java`, plus modelele din `Homework/` (`Publication`, `Subscription`, `SubscriptionCondition`, `DatasetGenerator`) |
| **Definiția schemei (a noastră)** | `Project/proto/publication.proto` |
| **Cod GENERAT (nu se scrie de mână, e „librărie”)** | `Project/src-gen/project/proto/Pubsub.java` — generat de `protoc` din `.proto` |
| **Librărie externă** | `Project/lib/protobuf-java-4.28.3.jar` (runtime-ul Protocol Buffers) |

> Notă pentru profesor: codul generat (`Pubsub.java`) a fost **comis** în repo intenționat,
> ca proiectul să compileze fără să fie nevoie de `protoc` instalat pe mașina de evaluare.
> Singurul lucru scris de noi pentru schemă este fișierul `.proto`.

---

## BONUS 1 — Serializare binară (Google Protocol Buffers) publisher → broker

### Ce cere enunțul
Folosirea unui mecanism de serializare binară (Protocol Buffers / Thrift) pentru
transmiterea **publicațiilor de la publisher la brokeri**.

### Ce s-a implementat și unde

1. **Schema** — `Project/proto/publication.proto`. Un singur mesaj `Publication` cu 8
   câmpuri (`pub_id`, `emit_timestamp_ms`, `hop_count`, `company`, `value`, `drop`,
   `variation`, `date`). `proto3`, `java_outer_classname = "Pubsub"` → clasa generată
   este `project.proto.Pubsub.Publication`.

2. **Codul generat** — `Project/src-gen/project/proto/Pubsub.java` (produs de `protoc`,
   comis în repo). Conține builder-ul și metodele de (de)serializare.

3. **Canalul binar pe broker** — `BinaryPubServer.java`. Deschide un `ServerSocket` pe
   portul `pub-port` (7001/2/3), acceptă conexiuni și, pe fiecare conexiune,
   **citește mesaje în buclă** cu `Pubsub.Publication.parseDelimitedFrom(input)`. Pentru
   fiecare mesaj decodat apelează `messageHandler.accept(message)`, care în broker este
   `Broker.handleBinaryPublication()`.

4. **Clientul binar pe publisher** — `BinaryPubClient.java`. Ține o conexiune TCP
   persistentă per broker (`ConcurrentHashMap<String, Sender>`), iar trimiterea se face
   cu `message.writeDelimitedTo(output)`.

5. **Integrarea în publisher** — `PublisherMain.java`:
   - parametru `--transport=protobuf|text` (default `protobuf`):
     `boolean useProtobuf = !transport.equalsIgnoreCase("text");`
   - construirea mesajului protobuf cu builder-ul:
     `Pubsub.Publication.newBuilder().setPubId(...).setCompany(...)....build();`
   - trimiterea: `binaryOutbound.send(target.host, target.port, message);`
   - **am păstrat și calea text** (`MessageCodec.buildPublication(...)` + `OutboundConnections`)
     ca să putem compara textul vs. binarul (vezi `compare-transport.ps1`).

6. **Recepția pe broker** — `Broker.handleBinaryPublication()` transformă mesajul
   protobuf înapoi în modelul de domeniu `homework.Publication` și apelează aceeași
   `processPublication(...)` ca și calea text. Astfel, **logica de rutare/matching este
   identică**, indiferent de serializare; doar transportul publisher→broker diferă.

### Framing-ul (detaliu pe care profesorul îl poate întreba)
Pe un socket TCP, mesajele protobuf nu au delimitatori naturali (e un stream de octeți).
De aceea folosim **length-delimited framing**: `writeDelimitedTo` scrie întâi lungimea
mesajului ca varint, apoi octeții; `parseDelimitedFrom` citește întâi lungimea, apoi
exact atâția octeți. Așa putem trimite multe publicații pe aceeași conexiune fără să le
amestecăm. Returnarea `null` din `parseDelimitedFrom` înseamnă „capăt de stream”
(conexiune închisă) și oprește bucla de citire.

### Măsurarea câștigului (ce raportăm)
În `PublisherMain` numărăm `bytesSent` și calculăm `avgBytesPerPublication`. La text,
dimensiunea este lungimea liniei UTF-8 + 1 (newline-ul); la protobuf,
`message.getSerializedSize()` + dimensiunea prefixului de lungime
(`CodedOutputStream.computeUInt32SizeNoTag(...)`), exact ce pune `writeDelimitedTo` pe fir.
Scriptul `compare-transport.ps1` rulează ambele moduri și pune rezultatele cap la cap.
Pe broker, `publicationsReceivedBinary` (din `Broker.writeStats`) dovedește că publicațiile
chiar au intrat pe calea binară.

### Structuri de date cheie (bonus 1)
- `BinaryPubClient.senders` : `ConcurrentHashMap<String, Sender>` — o conexiune
  persistentă per `host:port`, refolosită pentru toate publicațiile (evită overhead-ul
  de a deschide un socket per mesaj).
- `Sender` : socket + `BufferedOutputStream`/`BufferedInputStream`, sincronizat pe un
  `lock` (un publisher poate avea mai multe thread-uri).

### Întrebări probabile & răspunsuri
- **„De ce length-delimited?”** → TCP e stream, fără framing nu știi unde se termină un
  mesaj; vezi mai sus.
- **„De ce ai comis codul generat?”** → ca să compileze fără `protoc` pe mașina de
  evaluare; sursa de adevăr rămâne `.proto`.
- **„Protobuf vs text — ce câștigi?”** → mesaj binar compact (numere pe varint/8 octeți
  în loc de zecimale formatate ca text), parsare mai rapidă, schemă explicită/versionabilă.
- **„Doar publisher→broker e binar?”** → da, exact cum cere enunțul. Broker↔broker și
  broker→subscriber au rămas pe text (`MessageCodec`), ca să nu schimbăm restul protocolului.

---

## BONUS 2 — Toleranță la căderea unui nod broker (fără pierderea notificărilor)

### Ce cere enunțul
Tratarea căderilor de noduri broker astfel încât **să nu se piardă notificări**, cu o
simulare care **oprește efectiv** un broker.

### Ideea de design
O singură copie a unei subscripții, ținută pe un singur broker, dispare odată cu acel
broker. Soluția are **trei mecanisme complementare**, care împreună garantează livrarea
chiar dacă un broker pică:

1. **Replicarea subscripțiilor** (subscriber-side) — fiecare subscripție este trimisă la
   `--replicas` brokeri diferiți (primar + backup), nu doar la unul.
2. **Failover la publisher** — dacă brokerul țintă nu răspunde, publicația este reîncercată
   pe următorul broker.
3. **ACK la nivel de aplicație + dedup la subscriber** — publisher-ul consideră o
   publicație „livrată” doar după ce brokerul confirmă (ACK); subscriber-ul ignoră
   notificările duplicate care apar din replicare.

### Unde, în cod

**(1) Replicarea — `SubscriberMain.java`:**
```java
replicas = Math.max(1, Math.min(brokers.size(), replicas));   // se limitează la nr. de brokeri
...
for (int replica = 0; replica < replicas; replica++) {
    Endpoint broker = brokers.get((index + replica) % brokers.size());
    outbound.sendLine(broker.host, broker.port, message);
}
```
Fiecare subscripție merge la `replicas` brokeri consecutivi (round-robin pe `index`).
La `replicas=2`, subscripția există pe un broker primar **și** pe unul de rezervă, deci
dacă unul cade, celălalt încă face match și livrează.

**(2) Failover — `PublisherMain.java`:**
```java
int attempts = failover ? brokers.size() : 1;
...
for (int attempt = 0; attempt < attempts && !delivered; attempt++) {
    Endpoint target = brokers.get((startIndex + attempt) % brokers.size());
    delivered = binaryOutbound.send(target.host, target.port, message);  // sau textOutbound
    if (delivered && attempt > 0) failoversUsed++;
}
if (!delivered) publicationsDropped++;
```
Dacă brokerul ales nu confirmă, publicația este trimisă la următorul broker. Contorizăm
`failoversUsed` și `publicationsDropped` ca dovezi în statistici.

**(3) ACK + dedup:**
- ACK pe **server** — `BinaryPubServer.handleClient()`: după ce mesajul a fost
  procesat (`messageHandler.accept(message)`), brokerul scrie un octet `ACK` (`0x06`) și
  face flush. ACK-ul înseamnă „am preluat publicația”, deci publisher-ul nu o consideră
  pierdută.
- ACK pe **client** — `BinaryPubClient.sendAndWaitForAck()`: după `writeDelimitedTo`,
  publisher-ul **blochează** pe `input.read()` cu un timeout de socket
  (`socket.setSoTimeout(ackTimeoutMs)`, default 30s). Dacă primește `ACK` → `true`;
  altfel închide conexiunea și `send()` întoarce `false`, ceea ce declanșează failover-ul.
  Mai există și o **reîncercare pe aceeași țintă** (reconectare) înainte de a renunța,
  pentru cazul unei conexiuni TCP rupte tranzitoriu.
- Dedup pe **subscriber** — `SubscriberMain.Stats` (activ când `replicas > 1`): cheia de
  deduplicare este `(pubId, subId)`:
  ```java
  String key = line.substring(firstPipe+1, secondPipe)      // pubId
             + "|" + line.substring(thirdPipe+1, fourthPipe); // subId
  if (seen.putIfAbsent(key, Boolean.TRUE) != null) { duplicatesSuppressed++; return; }
  ```
  Structura: `ConcurrentHashMap<String, Boolean> seen`. Fără ea, o subscripție replicată
  pe 2 brokeri ar genera 2 notificări identice pentru aceeași publicație.

### De ce nu se pierd notificări (argumentul de corectitudine)
- Subscripția critică nu mai trăiește pe un singur broker (replicare) → căderea unui
  broker nu „șterge” interesul.
- Publicația nu se pierde dacă un broker e indisponibil → e redirecționată (failover),
  iar publisher-ul știe sigur că a fost preluată (ACK).
- Subscriberul nu numără de două ori → dedup pe `(pubId, subId)`.

### Simularea căderii — `simulate-broker-failure.ps1`
Rulează **trei scenarii** pe aceleași date:
- **A. Baseline** (fără cădere, `replicas=1`) — referința.
- **B. Cădere B2 FĂRĂ toleranță** (`replicas=1`, fără failover) — B2 este oprit cu
  `Stop-Process` (oprire **efectivă** a procesului) la secunda `KILL_AT_SECONDS` (default 12).
  Aici se vede pierderea de notificări (sub baseline).
- **C. Cădere B2 CU toleranță** (`replicas=2`, `failover=true`) — aceeași oprire a lui B2,
  dar notificările distincte revin la nivelul baseline.

Raportul generat (`failure-report.md`) pune într-un tabel: publicații emise, publicații
pierdute la publisher, failover-uri folosite, **notificări distincte livrate**, procent față
de baseline și duplicate suprimate. Diferența B vs. C este demonstrația cerută de enunț.

> Cum oprim „efectiv” brokerul: `Stop-Quietly` → `Stop-Process -Id $b2.Id -Force`. Nu e o
> oprire grațioasă; procesul e omorât, exact ca o cădere reală.

### Structuri de date cheie (bonus 2)
- `SubscriberMain.Stats.seen` : `ConcurrentHashMap<String, Boolean>` — set de chei
  `(pubId, subId)` văzute, pentru dedup.
- contoarele `publicationsDropped`, `failoversUsed` (publisher) și `duplicatesSuppressed`
  (subscriber) — toate scrise în fișierele `.stats`, ca dovezi numerice.

### Întrebări probabile & răspunsuri
- **„Unde se pot pierde totuși mesaje?”** → fereastra clasică e „publisher a trimis, broker
  a procesat, dar a căzut înainte de ACK”. ACK-ul mută granița: fără ACK, publisher-ul
  refolosește failover-ul; cu replicare, subscripția există și pe alt broker.
- **„De ce dedup pe `(pubId, subId)` și nu doar `pubId`?”** → un subscriber are mai multe
  subscripții; aceeași publicație poate face legitim match pe subscripții diferite și
  trebuie livrată o dată per subscripție. Duplicatul „de eliminat” e doar cel din replicare,
  adică aceeași pereche `(pubId, subId)`.
- **„De ce failover-ul nu produce inundație?”** → publisher-ul încearcă pe rând, se oprește
  la primul broker care confirmă (`&& !delivered`), nu trimite la toți.
- **„Replicare = broadcast?”** → nu. Subscripția merge la `replicas` brokeri (2), nu la toți;
  rutarea selectivă prin advertisement rămâne intactă.

---

## BONUS 3 — Filtrare pe conținut criptat (match pe subscripții/publicații criptate)

### Ce cere enunțul
Brokerul trebuie să poată **filtra/face match** fără să aibă acces la **conținutul** real
al publicațiilor și subscripțiilor (matching pe date criptate). Punctaj 5–10; noi țintim 10
acoperind **și egalitate, și intervale**.

### Ideea de design (cheia o au DOAR publisher + subscriber)
Brokerul nu primește niciodată cheia. Tot conținutul este transformat în **token-uri**
înainte să ajungă la broker, iar transformarea păstrează exact relațiile de care are nevoie
matching-ul:

- **Egalitate** (câmpuri text: `company`, `date`) → token **determinist** cu
  `HMAC-SHA256(cheie, "camp:valoare")`. Aceeași valoare ⇒ același token ⇒ brokerul rezolvă
  `=` / `!=` comparând token-uri, **fără să știe valoarea**.
- **Intervale** (câmpuri numerice: `value`, `drop`, `variation`) → cod
  **order-preserving (OPE)**: o transformare afină crescătoare `pantă · valoare + intercept`,
  cu `pantă > 0`. Pentru că funcția e strict crescătoare, ordinea se păstrează, deci `<`,
  `>`, `<=`, `>=` (și `=`) funcționează direct pe coduri.

Esențial: matching-ul rămâne **același cod** (`MatchingEngine`, `RoutingTable`, `Broker`).
Acestea operează pe valori opace; nu „știu” că sunt criptate. Criptarea se aplică **doar**
la publisher (pe publicații) și la subscriber (pe condițiile subscripției).

### Unde, în cod

**Toată cripto este în `MessageCrypto.java`.** Constructorul derivă din passphrase, prin
SHA-256, atât cheia HMAC cât și parametrii OPE:
```java
byte[] digest = sha256(passphrase);
this.hmacKey      = digest;
this.opeSlope     = Math.floorMod(readLong(digest, 0), 1000L) + 1L;   // panta > 0
this.opeIntercept = Math.floorMod(readLong(digest, 8), 1_000_000L);
```
- Token de egalitate:
  ```java
  public String equalityToken(String field, String value) {
      byte[] mac = hmac(field + ":" + value);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(mac).substring(0, 16);
  }
  ```
  (Prefixarea cu numele câmpului împiedică „să folosești un token de `company` ca token de
  `date`”.)
- Token de ordine (OPE):
  ```java
  public long orderToken(double value) {
      long scaled = Math.round(value * 1_000_000L);   // cuantizare, ca să prindem zecimalele
      return opeSlope * scaled + opeIntercept;        // afin, strict crescător
  }
  ```
- Criptarea unei publicații (`encrypt(Publication)`): `company`/`date` → `equalityToken`,
  iar `value`/`drop`/`variation` → `orderToken`. Rezultatul e tot un `homework.Publication`,
  doar că „valorile” sunt token-uri.
- Criptarea unei subscripții (`encrypt(Subscription)`): pentru fiecare condiție, dacă e câmp
  text → `SubscriptionCondition.text(field, op, equalityToken(...))`; dacă e numeric →
  `SubscriptionCondition.number(field, op, orderToken(...), 0)`. Operatorul (`=`, `<`, `>=`…)
  **nu** se schimbă.

**Integrarea (flag-uri noi, opționale, default off):**
- `PublisherMain.java`: `--encrypt=true --crypto-key=...`; după generarea publicațiilor,
  fiecare publicație e înlocuită cu varianta criptată (`crypto.encrypt(publications[i])`).
  Există și validare: `--crypto-key` e obligatoriu dacă `--encrypt=true`.
- `SubscriberMain.java`: aceleași flag-uri; după generare, fiecare subscripție e criptată
  înainte de a fi trimisă cu `SUB`.
- Brokerul **nu** are flag de cheie — intenționat.

**Dovada că brokerul stochează doar token-uri — `Broker.dumpStore()`** (apelat din
`Broker.stop(statsFile, dumpFile)`, declanșat de `--dump-store` în `BrokerMain.java`):
scrie fiecare subscripție locală ca `subId|condiții|host:port`. În clar vezi
`(company,=,"Apple")`; criptat vezi `(company,=,"ZWQf_bbVgQgBc6sJ")`.

### De ce matching-ul rămâne corect
- **Egalitate text**: `MatchingEngine.compareText` face `left.equals(right)`. Dacă atât
  publicația cât și subscripția au criptat aceeași valoare cu aceeași cheie, token-urile sunt
  identice → `equals` e adevărat. Pentru valori diferite, token-uri diferite → fals. Deci
  `=`/`!=` se comportă identic cu varianta în clar.
- **Intervale numerice**: `compareNumber` compară `double`-uri. Cum OPE e strict crescătoare,
  `valoare1 < valoare2 ⇔ token1 < token2`, deci `<,>,<=,>=` dau același rezultat ca în clar.
  Egalitatea numerică folosește un epsilon, dar token-urile aceleiași valori sunt identice
  (diferență 0), deci `=` merge.

**Validare**: în demo, numărul de notificări în clar == numărul în criptat (ex. 11205 = 11205),
ceea ce arată că semantica de matching e păstrată exact.

### Demonstrația — `demo-encrypted-matching.ps1`
Rulează același scenariu în două moduri: **plaintext** și **encrypted**. În ambele, brokerii
primesc `--dump-store` dar **NU** primesc `--crypto-key`. La final compară numărul de
notificări (trebuie să fie egal) și afișează primele intrări din store-ul lui B1 în clar vs.
criptat (lizibil vs. doar token-uri). Generează și `crypto-report.md`.

### Modelul de securitate (partea care aduce punctele — fii onest la prezentare)
**Ce NU vede brokerul**: conținutul real (numele companiei, data, valorile numerice). El
vede doar token-uri și poate doar să verifice egalitate/ordine.

**Ce „scurge” (leakage), recunoscut explicit**:
- criptarea **deterministă** (HMAC) pentru egalitate dezvăluie *tiparul de egalitate* și
  *frecvențele* (token-uri identice ⇒ valori identice), deci e vulnerabilă la analiză de
  frecvență;
- **OPE** dezvăluie *ordinea* valorilor (asta e chiar prețul ca `<`/`>` să funcționeze la
  broker); în plus, transformarea afină e recuperabilă cu suficiente perechi text-cunoscut →
  e un **demonstrator**, nu un cifru de producție.

**Cum s-ar întări** (de menționat ca direcție): scheme dedicate de *searchable encryption*,
*ORE* (order-revealing encryption) în locul OPE afine, sau *bucketization* cu zgomot pentru a
ascunde distribuția. Recunoașterea acestor limite este exact ce așteaptă profesorul la un
bonus „pe bune”.

### Structuri de date / decizii cheie (bonus 3)
- `MessageCrypto` ține: `byte[] hmacKey`, `long opeSlope`, `long opeIntercept` — toate derivate
  din passphrase, deci publisher și subscriber care folosesc aceeași cheie produc aceleași
  token-uri (condiție necesară pentru match).
- Token-urile de egalitate sunt truncheate la 16 caractere Base64-url — compromis între
  compactețe și probabilitate neglijabilă de coliziune pentru dataset-ul nostru.
- Câmpurile numerice criptate se serializează cu `decimals=0` (sunt deja întregi după OPE),
  iar `MessageCodec.decodeConditions` le citește ca numere — niciun cod de pe broker nu a
  trebuit schimbat.

### Întrebări probabile & răspunsuri
- **„Brokerul are cheia?”** → Nu, niciodată. O au doar publisher și subscriber (`--crypto-key`).
  Demo-ul pornește brokerii fără acel flag.
- **„Cum face match dacă nu vede conținutul?”** → pe egalitate de token-uri (HMAC determinist)
  și pe ordinea codurilor OPE; ambele păstrează exact relațiile cerute de operatori.
- **„De ce HMAC și nu o criptare reversibilă?”** → pentru egalitate nu ai nevoie de
  reversibilitate; ai nevoie ca aceeași intrare să dea același token. HMAC e determinist și
  legat de cheie, deci brokerul nu poate inversa token-ul în valoare.
- **„Care e slăbiciunea?”** → leakage de egalitate/frecvență (determinism) și de ordine (OPE);
  vezi modelul de securitate. E asumat și documentat.
- **„Ai modificat motorul de matching?”** → Nu. `MatchingEngine`/`RoutingTable`/`Broker`
  lucrează pe valori opace; criptarea trăiește doar la capete (publisher/subscriber).

---

## Anexă: cum reproduci fiecare bonus

```powershell
# Bonus 1 — comparație text vs protobuf
powershell -ExecutionPolicy Bypass -File Project\compare-transport.ps1

# Bonus 2 — simularea căderii unui broker (oprire efectivă a lui B2)
powershell -ExecutionPolicy Bypass -File Project\simulate-broker-failure.ps1

# Bonus 3 — matching pe conținut criptat (brokerii nu primesc cheia)
powershell -ExecutionPolicy Bypass -File Project\demo-encrypted-matching.ps1
```
Fiecare script compilează întâi sursele (`build.ps1`) și lasă artefactele (loguri, `.stats`,
rapoarte `.md`) în `Project/output/...`.
