# Generator echilibrat de publicatii si subscriptii

Acest repository contine implementarea temei.
## Cum se ruleaza

Rularea recomandata pentru verificare este prin scriptul automat din directorul `Homework`:

```bash
cd Homework
bash homework-test.bash
```

Pentru rularea completa, inclusiv benchmark-ul mare si scenariul izolat de rulare implicita:

```bash
cd Homework
bash homework-test.bash --full
```

Pe scurt:
- `bash homework-test.bash` ruleaza suita `quick`, adica testele functionale, de regresie si de consistenta;
- `bash homework-test.bash --full` ruleaza tot ce face `quick`, plus benchmark-ul mare si testul de rulare implicita in workspace izolat.

Scriptul:
- compileaza sursele Java;
- ruleaza automat scenariile functionale si de regresie;
- verifica frecventele campurilor, operatorii, reproducibilitatea cu `seed` si consistenta intre mai multe valori de paralelism;
- salveaza toate artefactele intr-un director de forma `Homework/output/automated-tests.XXXXXX/`.

## Unde se gasesc rezultatele

Fiecare scenariu testat genereaza propriul director in `Homework/output/automated-tests.XXXXXX/`. In interiorul fiecarui scenariu se gasesc:

- un `README.md` local pentru acel test;
- cate un subdirector `threads-N/` pentru fiecare factor de paralelism rulat;
- fisierele `publications.txt`, `subscriptions.txt` si `summary.txt`;
- fisierul `*.run.log` cu logul rularii.

Aceste fisiere `README.md` din output sunt rapoartele efective de executie cerute de tema: ele contin tipul de paralelizare, factorii de paralelism folositi, numarul de mesaje generate, timpii obtinuti si informatiile despre sistemul pe care s-a rulat testul.

## Structura programului pe scurt

Implementarea este organizata in `Homework/src/homework/`:

- `GeneratorConfig.java`:
  defineste configuratia generatorului: numarul de publicatii si subscriptii, procentele campurilor, procentul minim de `=` pentru `company`, valorile preset si intervalele numerice.
- `CommandLineOptions.java`:
  interpreteaza argumentele din linia de comanda si permite suprascrierea configuratiei implicite.
- `HomeworkApp.java`:
  coordoneaza executia programului, ruleaza benchmark-ul pentru valorile de thread-uri cerute si scrie fisierele `README.md` si `summary.txt`.
- `DatasetGenerator.java`:
  contine logica principala de generare pentru publicatii si subscriptii.
  Frecventele campurilor nu sunt obtinute prin random simplu pe fiecare subscriptie, ci prin planificarea exacta a numarului de aparitii, apoi distributia aleatoare a pozitiilor.
  Tot aici este implementata si paralelizarea cu thread-uri.
- `Publication.java`:
  modelul unei publicatii cu structura fixa.
- `Subscription.java`:
  modelul unei subscriptii, care poate contine doar o parte dintre campuri.
- `SubscriptionCondition.java`:
  modelul unei conditii individuale dintr-o subscriptie.
- `BenchmarkResult.java`:
  retine rezultatele unei rulari pentru raportare.
- `SystemInfo.java`:
  colecteaza informatiile despre procesor si mediul de rulare pentru `README`.