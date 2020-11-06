sha="$(git ls-remote https://github.com/jacobobryant/biff.git | awk '/HEAD/ { print $1 }')"
deps="{:deps {github-jacobobryant/biff {:git/url \"https://github.com/jacobobryant/biff\" :sha \"$sha\"}}}"
clj -Sdeps "$deps" -M -m biff.project
