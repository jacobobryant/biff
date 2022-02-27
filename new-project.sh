if ! which rlwrap > /dev/null; then
  echo 'Please install rlwrap (a dependency of clj).'
  exit 1
fi

if ! clj --help | grep -q -- ' -X'; then
  echo It looks like you have an old version of clj that doesn\'t support the -X option.
  echo Please install at least version 1.10.1.697.
  exit 2
fi

# TODO switch dev to HEAD
sha="$(git ls-remote https://github.com/jacobobryant/biff.git dev | awk '{ print $1 }')"
deps="{:deps {github-jacobobryant/biff-new-project
              {:git/url \"https://github.com/jacobobryant/biff\"
               :sha \"$sha\"
               :deps/root \"new-project\"}}}"
clj -Sdeps "$deps" -M -m com.biffweb.new-project
