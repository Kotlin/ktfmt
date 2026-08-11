// MAX_WIDTH 66

private fun parseRequest(
  isWrapped: Boolean,
  json: Json,
  inputText: String,
) =
  if (isWrapped) {
      runCatching { json.decodeFromString<Request>(inputText) }
        .mapCatching {
          requireNotNull(it.body) {
            "Request#body must not be null or empty"
          }
          it.body!!
        }
        .fold({ Success(it) }, { Failure(it) })
    } else {
      runCatching {
          json.decodeFromString<AnotherRequest>(inputText)
        }
        .fold({ Success(it) }, { Failure(it) })
    }
    .mapFailure {
      // slightly long text here that is an example of a comment
      Response(false, 400, listOfNotNull(it.message))
    }
