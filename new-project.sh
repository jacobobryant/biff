if ! which clj > /dev/null; then
  echo '`clj` command not found. Please install it here: https://clojure.org/guides/getting_started'
  exit 1
fi

if ! which rlwrap > /dev/null; then
  echo '`rlwrap` command not found. Please install it.'
  exit 2
fi

if ! clj --help | grep -q -- ' -X'; then
  echo It looks like you have an old version of clj that doesn\'t support the -X option.
  echo Please install at least version 1.10.1.697.
  exit 3
fi

JAVA_MAJOR_VERSION=$(java -version 2>&1 | grep -oP 'version "?(1\.)?\K\d+' || true)
if [[ $JAVA_MAJOR_VERSION -lt 11 ]]; then
  echo Please install Java 11 or higher.
  exit 4
fi

# TODO switch dev to HEAD
sha="$(git ls-remote https://github.com/jacobobryant/biff.git dev | awk '{ print $1 }')"
deps="{:deps {github-jacobobryant/biff-new-project
              {:git/url \"https://github.com/jacobobryant/biff\"
               :sha \"$sha\"
               :deps/root \"new-project\"}}}"
clj -Sdeps "$deps" -M -m com.biffweb.new-project
