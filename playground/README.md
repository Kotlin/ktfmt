# ktfmt playground

```bash
./playground/run.sh
```
Open <http://localhost:8000> and play with it.


## Implementation details

Under the hood, it runs `./gradlew :ktfmt:shadowJar` and starts a trivial
python server that serves formatting requests by invoking `java -jar` on the
built fat jar. Easy to experiment with a new formatter and compare
