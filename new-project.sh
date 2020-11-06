sha="$(git ls-remote https://github.com/jacobobryant/biff.git HEAD | awk '{ print $1 }')"
deps="{:deps {github-jacobobryant/biff {:git/url \"https://github.com/jacobobryant/biff\" :sha \"$sha\"}}}"
clj -Sdeps "$deps" -M -m biff.project
