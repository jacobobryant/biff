if ! which rlwrap > /dev/null; then
  echo 'Please install rlwrap (a dependency of clj).'
  exit 1
fi

if ! which npm > /dev/null; then
  echo 'npm not found. Please install node.js.'
  exit 2
fi

if ! clj --help | grep -q -- ' -X'; then
  echo It looks like you have an old version of clj that doesn\'t support the -X option.
  echo Please install at least version 1.10.1.697.
  exit 3
fi

sha="$(git ls-remote https://github.com/jacobobryant/biff.git HEAD | awk '{ print $1 }')"
deps="{:deps {biff/tasks
              {:git/url \"https://github.com/jacobobryant/biff\"
               :sha \"$sha\"
               :deps/root \"libs/tasks\"}}}"
clj -Sdeps "$deps" -X biff.tasks/new-project
