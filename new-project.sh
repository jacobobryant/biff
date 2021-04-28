sha="$(git ls-remote https://github.com/jacobobryant/biff.git HEAD | awk '{ print $1 }')"
deps="{:deps {biff/tasks
              {:git/url \"https://github.com/jacobobryant/biff\"
               :sha \"$sha\"
               :deps/root \"libs/tasks\"}}}"
clj -Sdeps "$deps" -X biff.tasks/new-project
