# ktfmt playground

```bash
./playground/run.sh
```
Open <http://localhost:8000> and play with it.


## Implementation details

Under the hood, it runs `./gradlew :ktfmt:nativeCompile` and starts a trivial
python server that serves formatting requests using the built native image.
Close to zero latency, easy to experiment with a new formatter and compare
