# Biff

Biff is a web framework and self-hosted deployment solution for Clojure,
inspired heavily by Firebase. See [findka.com/biff](https://findka.com/biff/).

## Contributing

PRs welcome, especially if you want to tackle some of the current issues (there are several that I don't think would require too much time). If you're planning something significant, you might want to bring it up in `#biff` on Clojurians Slack.

The easiest way to hack on Biff is to start a new project (with `new-project.sh`) and then change the Biff dependency in `deps.edn` to `{:local/root "/path/to/cloned/biff/repo" ...}`. Then just run `./task init; ./task dev`. Eval `(biff.core/refresh)` as needed.

### Documentation

Prereqs: See [slate/README.md](slate/README.md). You'll need Ruby; then run:

```shell
cd slate
gem install bundler
bundle install
```

After that, you can just run `./task docs-dev` to work on the documentation.

## License

Distributed under the [EPL v2.0](LICENSE)

Copyright &copy; 2020 [Jacob O'Bryant](https://jacobobryant.com).
