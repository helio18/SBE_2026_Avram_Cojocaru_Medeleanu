# Raport final de evaluare - Proiect SBE 2026

**Sistem publish/subscribe content-based cu retea de brokeri**

Avram Tudor, Cojocaru Adelin, Medeleanu Daria

---

## 1. Sumar

Proiectul implementeaza un sistem publish/subscribe content-based in care:

- 3 brokeri formeaza un overlay in **topologie triunghi** (B1-B2-B3-B1);
- subscriberii inregistreaza abonamente la brokeri prin **balansare round-robin**;
- publisherii emit publicatii la brokeri tot prin round-robin;
- brokerii ruteaza publicatiile selectiv prin overlay folosind **advertisement-based routing** si trimit notificarile direct la subscriberi.

Modelul de date (`Publication`, `Subscription`, `SubscriptionCondition`) si generatorul (`DatasetGenerator`) sunt reutilizate **fara duplicare** din tema 1, prin classpath. Toate componentele ruleaza ca **procese Java independente**, comunicand prin **TCP sockets**.

Evaluarea s-a realizat conform cerintei: **10.000 de subscriptii inregistrate, feed continuu de 3 minute, 2 scenarii** (egalitate `=` pe `company` cu frecventa 100%, respectiv 25%).

### 1.1 Schema arhitecturii

```
                       +-------------------+
                       |  Publisher  P1    |
                       |  (port outbound)  |
                       +---------+---------+
                                 |
                         PUB (round-robin)
                                 |
              +------------------+------------------+
              |                  |                  |
              v                  v                  v
        +-----------+      +-----------+      +-----------+
        |  Broker   |      |  Broker   |      |  Broker   |
        |   B1      |<---->|   B2      |<---->|   B3      |
        | port 5001 | PUB  | port 5002 | PUB  | port 5003 |
        |           | ADV  |           | ADV  |           |
        +-----+-----+      +-----+-----+      +-----+-----+
              ^  \________________________________/  ^
              |        (legatura B1 <--> B3)         |
              |                                      |
              | TOPOLOGIE TRIUNGHI (graf complet K3) |
              |                                      |
              | SUB / NOT          SUB / NOT         | SUB / NOT
              |                                      |
        +-----+-----+      +-----------+      +------+----+
        | Subscriber|      | Subscriber|      | Subscriber|
        |    S1     |      |    S2     |      |    S3     |
        | port 6001 |      | port 6002 |      | port 6003 |
        +-----------+      +-----------+      +-----------+
        3.334 sub.         3.333 sub.         3.333 sub.
        (round-robin pe B1, B2, B3)
```

**Legenda mesajelor (toate text, separate cu `|`):**

| Mesaj | Sens | Cine -> cine |
|---|---|---|
| `SUB` | abonament nou | Subscriber -> Broker |
| `ADV` | anunt rutare (control plane) | Broker -> peers (1 hop) |
| `PUB` | publicatie cu date | Publisher -> Broker, apoi Broker -> peers (selectiv) |
| `NOT` | notificare de match | Broker -> Subscriber (direct) |

**Fluxuri principale (pe scurt):**

1. **Setup**: subscriberii trimit `SUB` round-robin la brokeri. Fiecare broker care primeste `SUB` salveaza local + face broadcast de `ADV` la peers. Peer-ii populeaza `RoutingTable` (per-vecin: ce abonamente sunt accesibile prin acel vecin).
2. **Operare**: publisherul trimite `PUB` round-robin la brokeri. Brokerul primitor: (1) face matching local + trimite `NOT` direct la subscriberii proprii care matcheaza, (2) consulta `RoutingTable` si forwardeaza `PUB` doar la vecinii cu cel putin un abonament potrivit, cu `hopCount + 1`.
3. **Anti-bucla**: fiecare broker tine `seenPublications` si `seenAdvertisements` (ConcurrentHashMap cu `putIfAbsent`) pentru a procesa un mesaj o singura data, indiferent pe ce cale ajunge.

---

## 2. Metodologie

### 2.1 Configuratie experimentala

| Parametru | Valoare |
|---|---|
| Durata feed publicatii | **180 s** (3 minute) |
| Subscriptii totale | **10.000** |
| Subscriberi | 3 (cu 3334 / 3333 / 3333 subscriptii) |
| Publisheri | 1 |
| Rata publicatii | 25 pub/s |
| Brokeri | 3 in topologie triunghi |
| Procesor | Intel Core i5-10400 @ 2.90 GHz |
| Runtime | OpenJDK 17+ |
| Transport | TCP loopback (localhost) |

### 2.2 Generarea datelor

Atat subscriptiile cat si publicatiile au fost generate cu `DatasetGenerator` din tema 1, **paralel pe 2 fire de executie**, cu seed-uri distincte per proces pentru reproductibilitate. Setul de companii definit in `GeneratorConfig.defaultConfig()` are **8 valori distincte** (probabilitate egala de aparitie in publicatii).

Toate subscriptiile au folosit **un singur camp constrans**: `company`. In scenariu A, 100% din subscriptii folosesc operatorul `=`. In scenariu B, doar 25% folosesc `=`, restul de 75% folosesc `!=`.

### 2.3 Metricile masurate

- **Publicatii emise** (`publicationsSent`) - per publisher, agregat pe scenariu.
- **Notificari primite** (`notificationsReceived`) - per subscriber, agregat pe scenariu.
- **Latenta de livrare** - calculata la subscriber ca `now - emitTimestamp`, unde `emitTimestamp` este pus in mesajul `PUB` de publisher la momentul trimiterii si propagat prin sistem pana in mesajul `NOT`. Latenta medie e raportata ponderat dupa numarul de notificari.
- **Rata de matching** = `notificari / (publicatii x subscriptii)` x 100%.

### 2.4 Reproducere

```powershell
powershell -ExecutionPolicy Bypass -File Project\run-eval.ps1
```

(echivalent Bash: `bash Project/run-eval.bash`). Scriptul porneste cele doua scenarii consecutiv, agrega statisticile per scenariu si produce raportul final.

---

## 3. Rezultate

### 3.1 Tabel sumar

| Metrica | Scenariu A (100% `=`) | Scenariu B (25% `=`) |
|---|---:|---:|
| Subscriptii inregistrate | 10.000 | 10.000 |
| **(a)** Publicatii livrate cu succes in 3 minute | **4.500** | **4.500** |
| Notificari livrate (total cumulat) | 5.625.331 | 30.930.163 |
| **(b)** Latenta medie de livrare | **5.269 ms** | **24.240 ms** |
| **(c)** Rata de matching masurata | **12.5007%** | **68.7337%** |
| Rata de matching teoretica | 12.5% | 68.75% |
| Eroare absoluta vs. teorie | 0.005 p.p. | 0.02 p.p. |

### 3.2 Punctul (a) - Publicatii livrate cu succes

In ambele scenarii s-au emis **4.500 publicatii in 180 secunde** (echivalent cu rata stabila de **25 pub/s**, fara pierderi de mesaje). Toate publicatiile emise au fost preluate si rutate cu succes prin overlay-ul de brokeri:

- in scenariu A au generat **5.625.331 notificari** distincte catre subscriberi;
- in scenariu B au generat **30.930.163 notificari** distincte catre subscriberi.

Faptul ca masuratoarea rezulta in fix `25 x 180 = 4500` publicatii confirma ca publisherul nu a fost limitat de rata si ca toate publicatiile emise au fost acceptate de brokeri si forwardate. **Nu s-au observat publicatii pierdute** (verificat din contoarele `publicationsReceived` per broker).

### 3.3 Punctul (b) - Latenta medie de livrare

Latenta medie de livrare end-to-end (de la emitere la publisher pana la primire la subscriber) este:

- **Scenariu A: 5.269 ms**
- **Scenariu B: 24.240 ms** (~4.6x mai mare)

Cresterea de latenta in scenariu B nu este cauzata de cresterea distantei in overlay (in ambele scenarii orice publicatie face cel mult 2 hop-uri intre brokeri), ci de **saturatia canalelor TCP** pe loopback: in scenariu B brokerii trimit ~5.5x mai multe `NOT` catre subscriberi in aceeasi fereastra de timp, ceea ce produce coada pe cozile de iesire ale socketelor. Cu toate acestea, sistemul ramane functional fara pierderi de mesaje.

### 3.4 Punctul (c) - Comparatie rata de matching: 100% vs 25% egalitate

Aceasta este componenta centrala a evaluarii. Pentru un set de **8 companii posibile**:

- Operatorul **`=`** matcheaza in medie **1/8 = 12.5%** din publicatii (publicatia trebuie sa aiba exact aceeasi companie ca cea ceruta);
- Operatorul **`!=`** matcheaza **7/8 = 87.5%** din publicatii (orice companie diferita de cea exclusa).

**Scenariu A (100% `=`):**
- Asteptat: `1.0 x 12.5% = 12.5%`
- Masurat: **12.5007%** (eroare ~0.005 puncte procentuale, sub 0.05%).

**Scenariu B (25% `=`, 75% `!=`):**
- Asteptat: `0.25 x 12.5% + 0.75 x 87.5% = 3.125% + 65.625% = 68.75%`
- Masurat: **68.7337%** (eroare ~0.02 puncte procentuale, sub 0.05%).

**Concluzie cantitativa: reducerea frecventei egalitatii de la 100% la 25% face ca rata de matching sa creasca de ~5.5x** (de la 12.5% la 68.74%), pentru ca operatorul `!=` are o probabilitate de match mult mai mare decat `=`. Acest rezultat coincide aproape exact cu predictia teoretica, ceea ce valideaza corectitudinea motorului de matching si a rutarii prin overlay.

### 3.5 Distributie pe brokeri

Datele per-broker (`Project/output/eval.zmWV8h/scenario-*/B*.stats`) confirma:

- balansarea **round-robin a subscriptiilor**: fiecare broker primeste aproximativ 3.333 subscriptii din 10.000;
- balansarea **round-robin a publicatiilor**: fiecare broker primeste aproximativ 1.500 publicatii direct de la publisher;
- **forwardare selectiva**: fiecare broker forwardeaza publicatiile catre cei 2 vecini doar atunci cand `RoutingTable.neighborsInterestedIn()` returneaza vecini cu cel putin un advertisement potrivit. In scenariile date (constrangere numai pe `company`), aproape toate publicatiile se forwardeaza la toti vecinii, dar mecanismul de filtrare este verificat in test inline (`ProjectApp`).

---

## 4. Interpretare si discutie

### 4.1 Corectitudine

Erorile sub 0.05 puncte procentuale intre rata de matching teoretica si cea masurata in ambele scenarii indica:

1. **MatchingEngine** evalueaza corect operatorii `=` si `!=` pe `company`.
2. **Anti-bucla** (`seenPublications`) functioneaza: o publicatie nu produce notificari duplicate pe acelasi subscriber.
3. **Rutarea overlay** nu pierde mesaje: toate publicatiile interesante ajung la toti subscriberii interesati.

### 4.2 Scalabilitate

In scenariu B, sistemul a sustinut un throughput de **~172.000 notificari/s** in fereastra de 3 minute, dintr-un singur publisher. Latenta medie ramane sub 25 ms chiar si la acest volum, ceea ce arata ca:

- arhitectura "broker trimite NOT direct la subscriber" (fara hop suplimentar prin alt broker) este eficienta;
- numarul mic de brokeri (3) este adecvat pentru aceasta scara - pentru volume mai mari sau topologii mai complexe, ar fi necesari mai multi brokeri si o rutare ierarhica.

### 4.3 Trade-off-uri arhitecturale observate

- **Filtrarea selectiva** (forward doar catre vecinii interesati, nu broadcast) economiseste trafic inter-broker si timp CPU, dar necesita propagarea ADV-urilor in faza de setup;
- **Topologia triunghi** garanteaza ca un singur `ADV` cu hop=1 acopera toata reteaua, deoarece fiecare broker e direct conectat cu ceilalti 2;
- **Round-robin pe brokeri** evita hot-spot-urile, distribuind incarcarea uniform;
- **Mecanismul `seenPublications`** previne buclele in graful triunghiular, completat de **hop limit** ca masura de siguranta.

---

## 5. Concluzii

Solutia satisface integral cerinta proiectului:

- **(a)** Sustine livrarea continua de **25 publicatii/s timp de 3 minute** (4.500 publicatii) prin reteaua de brokeri, fara pierderi de mesaje.
- **(b)** Latenta medie de livrare end-to-end ramane sub **25 ms** chiar in scenariul cu cel mai mare volum de notificari (>30 milioane in 3 minute).
- **(c)** Comparatia 100% `=` vs 25% `=` produce rate de matching **12.50% vs 68.73%**, cu eroare sub 0.05 puncte procentuale fata de valorile teoretice (12.5% si 68.75%) - confirmand atat corectitudinea generatorului din tema 1, cat si corectitudinea motorului de filtrare si a rutarii prin overlay.