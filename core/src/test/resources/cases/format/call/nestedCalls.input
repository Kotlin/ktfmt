// MAX_WIDTH 70
// TRAILING_COMMA_STRATEGY NONE

function(
    param =
        (rate downTo min step step).drop(1).map {
          nestedFun(
              rate =
                  rate(
                      value =
                          firstArg<Input>().info.get(0).rate.value))
        })
