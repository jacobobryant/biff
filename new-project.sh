sha="$(git ls-remote https://github.com/jacobobryant/biff.git HEAD | awk '{ print $1 }')"
deps="{:deps {github-jacobobryant/biff {:git/url \"https://github.com/jacobobryant/biff\" :sha \"$sha\"}}}"
chkapi=`clj -Sdescribe | grep repl-aliases`
mopt="" && [[ -n $chkapi ]] && mopt="-M"
clj -Sdeps "$deps" "$mopt" -m biff.project
