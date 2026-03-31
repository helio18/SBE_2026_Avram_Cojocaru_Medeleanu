# Homework - Sistem publish/subscribe content-based

Acest proiect extinde generatorul initial de date intr-o simulare completa de sistem pub/sub.
Implementarea acopera partea non-bonus din cerinta: publisheri, brokeri, subscriberi,
rutare intre brokeri si evaluare pentru doua scenarii de matching.

## Arhitectura implementata

- `2` publisheri care emit un flux continuu de publicatii generate din generatorul initial
- `3` brokeri intr-un overlay de tip inel; fiecare broker stocheaza doar o parte din subscriptii
- `3` subscriberi simulati care se conecteaza aleatoriu la brokeri pentru a inregistra subscriptii
- subscriptiile aceluiasi subscriber sunt distribuite balansat pe brokeri printr-un mecanism round-robin
- fiecare publicatie trece prin tot overlay-ul, fiecare broker facand matching doar pe subscriptiile locale

## Configuratia evaluarii

- subscriptii simple inregistrate per scenariu: `10000`
- pool de publicatii: `5000`
- brokeri: `3`
- publisheri: `2`
- subscriberi: `3`
- durata feed-ului continuu: `180` secunde
- interval intre doua publicatii emise de acelasi publisher: `50` ms

## Rezultate scenarii

| Scenariu | Egalitate company | Publicatii emise | Livrari reusite | Publicatii potrivite | Latenta medie ms | Matching rate | Hop-uri medii publicatii |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| equals-100 | 100% | 7088 | 3182 | 2710 | 0.3592 | 38.2336% | 3.00 |
| equals-25 | 25% | 7142 | 21426 | 7142 | 0.0088 | 100.0000% | 3.00 |

## Interpretare

- `Livrari reusite` = numarul total de notificari trimise subscriberilor prin reteaua de brokeri
- `Publicatii potrivite` = numarul de publicatii care au avut cel putin un match
- `Matching rate` = procentul de publicatii care au avut cel putin un match
- comparatia intre scenariul `100% =` si `25% =` arata impactul distributiei operatorului `=` asupra matching-ului

## Balansare subscriptii pe brokeri

- broker-1: `3334` subscriptii stocate
- broker-2: `3333` subscriptii stocate
- broker-3: `3333` subscriptii stocate
- hop-uri medii la inregistrare: `2.00`

## Fisiere generate

- scenariu `equals-100`:
  - `output/scenario-equals-100/publication-pool.txt`
  - `output/scenario-equals-100/simple-subscriptions.txt`
  - `output/scenario-equals-100/scenario-report.txt`
- scenariu `equals-25`:
  - `output/scenario-equals-25/publication-pool.txt`
  - `output/scenario-equals-25/simple-subscriptions.txt`
  - `output/scenario-equals-25/scenario-report.txt`

## Specificatii masina

- CPU: `12th Gen Intel(R) Core(TM) i7-12650H`
- logical cores: `16`
- OS: `Linux 6.8.0-106-generic`
- Java: `21.0.10`
- raport generat la: `2026-03-30 11:58:54`
