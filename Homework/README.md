# Homework - Generator de publicatii si subscriptii

Acest proiect implementeaza integral cerinta temei in Java, in VS Code.
Generatorul produce seturi de publicatii si subscriptii, salveaza rezultatele in fisiere text,
ruleaza benchmark cu mai multe niveluri de paralelizare si scrie automat acest raport.

## Decizii de implementare

- paralelizare: `threads`
- limbaj: `Java 21`
- structura publicatie: fixa, cu campurile `company`, `value`, `drop`, `variation`, `date`
- distributia campurilor din subscriptii este controlata exact pe baza unor tinte intregi planificate, nu doar random
- cand un procent nu poate fi reprezentat exact pentru dimensiunea setului, se foloseste cea mai apropiata distributie fezabila
- pentru campul `company`, operatorul `=` este controlat separat si respecta pragul minim cerut
- fiecare subscriptie contine cel putin un camp

## Configuratie folosita

- publicatii generate: `40000`
- subscriptii generate: `40000`
- thread-uri testate: `1, 4`
- frecvente campuri in subscriptii:
  - company: `90%`
  - value: `70%`
  - drop: `55%`
  - variation: `65%`
  - date: `40%`
- prag minim pentru operatorul `=` pe `company`: `70%`

## Benchmark

| Threads | Publicatii ms | Subscriptii ms | Scriere ms | Total ms | Speedup |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 | 19 | 175 | 186 | 381 | 1.00x |
| 4 | 4 | 54 | 89 | 148 | 2.57x |

## Verificare distributii

Valorile de mai jos provin din rularea cu `4` thread-uri.

| Camp | Cerut | Tinta discreta | Obtinut |
| --- | ---: | ---: | ---: |
| company | 90% | 36000 (90.00%) | 36000 (90.00%) |
| value | 70% | 28000 (70.00%) | 28000 (70.00%) |
| drop | 55% | 22000 (55.00%) | 22000 (55.00%) |
| variation | 65% | 26000 (65.00%) | 26000 (65.00%) |
| date | 40% | 16000 (40.00%) | 16000 (40.00%) |

- `company` cu operator `=` cerut: `70%`
- `company` cu operator `=` tinta discreta minima: `25200 / 36000` = `70.00%`
- `company` cu operator `=` obtinut: `25200 / 36000` = `70.00%`

## Specificatii masina

- CPU: `12th Gen Intel(R) Core(TM) i7-12650H`
- logical cores: `16`
- OS: `Linux 6.8.0-106-generic`
- Java: `21.0.10`
- raport generat la: `2026-03-31 20:13:30`

## Fisiere generate

- run `1` thread-uri:
  - `output/threads-1/publications.txt`
  - `output/threads-1/subscriptions.txt`
  - `output/threads-1/summary.txt`
- run `4` thread-uri:
  - `output/threads-4/publications.txt`
  - `output/threads-4/subscriptions.txt`
  - `output/threads-4/summary.txt`

## Rulare

Din terminal, din folderul `Homework`:

```bash
find src -name '*.java' -print0 | xargs -0 javac -d bin
java -cp bin homework.HomeworkApp
```

Exemplu pentru testare rapida cu parametri custom:

```bash
java -cp bin homework.HomeworkApp --publications=1000 --subscriptions=1000 --threads=1,4 --output=output/test-small
```
