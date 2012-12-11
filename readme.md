# jnput

FIXME: write description

## Browser REPL setup

### Terminal
* lein cljsbuild auto
* python -m SimpleHTTPServer 8888 and go to localhost:8888

### Emacs
* `(defun browser-repl () (interactive)
           (run-lisp "LEIN-DIR trampoline cljsbuild repl-listen"))`
* M-x browser-repl and eval (repl/connect ...)

## Usage

FIXME: write

## License

Copyright (C) 2012 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
