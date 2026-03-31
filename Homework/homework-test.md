# Cum testez aplicatia

Toate comenzile de mai jos se ruleaza din folderul `Homework`.

Observatie:
- `--mode=generator` testeaza generatorul initial de date
- `--mode=project` testeaza arhitectura publish/subscribe ceruta in varianta extinsa

## 1. Compilare

Comanda:

```bash
find src -name '*.java' -print0 | xargs -0 javac -d bin
```

Ce demonstreaza:
- proiectul se compileaza corect
- toate clasele necesare exista si sunt legate corect intre ele

## 2. Afisarea optiunilor disponibile

Comanda:

```bash
java -cp bin homework.HomeworkApp --help
```

Ce demonstreaza:
- aplicatia poate fi configurata din linia de comanda
- pot modifica numarul de publicatii, subscriptii, thread-uri, procentele si output-ul

## 3. Rularea standard a temei

Comanda:

```bash
java -cp bin homework.HomeworkApp --mode=generator
```

Ce demonstreaza:
- aplicatia genereaza publicatii si subscriptii
- publicatiile au structura fixa
- subscriptiile au structura variabila
- se genereaza fisiere text
- se face benchmark pentru `1` si `4` thread-uri
- se actualizeaza `output/README.md` cu rezultate

Verificari utile dupa rulare:

```bash
wc -l output/threads-1/publications.txt output/threads-1/subscriptions.txt
wc -l output/threads-4/publications.txt output/threads-4/subscriptions.txt
sed -n '1,5p' output/threads-4/publications.txt
sed -n '1,5p' output/threads-4/subscriptions.txt
sed -n '1,200p' output/threads-4/summary.txt
```

Ce demonstreaza in plus:
- numarul de mesaje generate este corect
- fisierele au continut valid
- sumarul arata procentele obtinute si timpii

## 4. Test rapid pe set mic de date

Comanda:

```bash
java -cp bin homework.HomeworkApp --mode=generator --publications=1000 --subscriptions=1000 --threads=1,4 --output=output/test-small
```

Ce demonstreaza:
- aplicatia functioneaza si pe un caz mic, usor de verificat
- benchmark-ul merge si pe alta configuratie
- output-ul poate fi pus in alt director

Verificare:

```bash
sed -n '1,200p' output/test-small/threads-4/summary.txt
```

## 5. Test de margine pentru procente

Comanda:

```bash
java -cp bin homework.HomeworkApp --mode=generator --publications=500 --subscriptions=500 --threads=1,2 --company-frequency=100 --value-frequency=100 --drop-frequency=0 --variation-frequency=0 --date-frequency=0 --company-equals=80 --output=output/test-edge
```

Ce demonstreaza:
- un camp poate aparea in `100%` din subscriptii
- un camp poate lipsi complet, adica `0%`
- pragul minim pentru operatorul `=` pe `company` este respectat
- configuratia procentelor este aplicata corect

Verificari:

```bash
sed -n '1,200p' output/test-edge/threads-2/summary.txt
sed -n '1,5p' output/test-edge/threads-2/subscriptions.txt
```

## 6. Test de paralelizare

Comanda:

```bash
java -cp bin homework.HomeworkApp --mode=generator --publications=100000 --subscriptions=100000 --threads=1,4,8 --output=output/test-benchmark
```

Ce demonstreaza:
- aplicatia suporta mai multe niveluri de paralelizare
- timpii pot fi comparati intre `1`, `4` si `8` thread-uri
- cerinta cu evaluarea performantelor este acoperita

Verificare:

```bash
sed -n '1,220p' output/test-benchmark/README.md
```

## 7. Test de reproductibilitate

Comenzi:

```bash
java -cp bin homework.HomeworkApp --mode=generator --publications=2000 --subscriptions=2000 --threads=1 --seed=123 --output=output/repro-1
java -cp bin homework.HomeworkApp --mode=generator --publications=2000 --subscriptions=2000 --threads=4 --seed=123 --output=output/repro-4
diff -q output/repro-1/threads-1/publications.txt output/repro-4/threads-4/publications.txt
diff -q output/repro-1/threads-1/subscriptions.txt output/repro-4/threads-4/subscriptions.txt
```

Ce demonstreaza:
- cu acelasi `seed` se obtin aceleasi date
- paralelizarea nu schimba continutul generat, doar timpul de executie

## 8. Ce arat la final

Comanda pentru proiectul pub/sub:

```bash
java -cp bin homework.HomeworkApp --mode=project --publications=1000 --subscriptions=2000 --evaluation-seconds=3 --publish-interval-ms=10 --output=output/project-demo
```

Ce demonstreaza:
- exista publisheri, brokeri si subscriberi
- subscriptiile sunt distribuite balansat pe brokeri
- publicatiile trec prin mai multi brokeri
- exista doua scenarii de evaluare: `100%` si `25%` egalitate
- se genereaza raport de evaluare pentru proiectul extins

Fisierele pe care le pot deschide la prezentare:
- `output/generator-default/README.md`
- `output/generator-default/threads-4/summary.txt`
- `output/generator-default/threads-4/publications.txt`
- `output/generator-default/threads-4/subscriptions.txt`
- `output/project-demo/README.md`
- `output/project-demo/scenario-equals-100/scenario-report.txt`
- `output/project-demo/scenario-equals-25/scenario-report.txt`

Concluzia pe scurt:
- tema genereaza publicatii si subscriptii corect
- procentele cerute sunt respectate exact
- exista paralelizare cu thread-uri
- exista masurare de timp
- exista si simularea pub/sub cu brokeri, subscriberi si evaluare pe scenarii
