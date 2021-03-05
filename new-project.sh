sha="$(git ls-remote https://github.com/jacobobryant/biff.git HEAD | awk '{ print $1 }')"
deps="{:deps {github-jacobobryant/biff {:git/url \"https://github.com/jacobobryant/biff\" :sha \"$sha\"}}}"
mopt=""
if clj -Sdescribe | grep -q repl-aliases; then
  mopt="-M"
fi
clj -Sdeps "$deps" $mopt -m biff.project
