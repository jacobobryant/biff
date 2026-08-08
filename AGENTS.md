Don't write or update READMEs, docstrings, or in-code comments unless I
explicitly tell you to.

Try to avoid binding the entire `ctx` map when possible. e.g. doing (defn foo
[{:keys [a b c]}] ...) instead of (defn foo [ctx] ...) means that you can see
immediately what's being used. And then instead of passing ctx to other
functions you can pass explicit maps like {:a a :b b} etc.
