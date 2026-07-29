-- Auto-generated; do not edit.

CREATE TABLE biff_sqlite_kv (
  id BLOB PRIMARY KEY NOT NULL,
  k TEXT NOT NULL,
  namespace TEXT NOT NULL,
  v BLOB NOT NULL,
  UNIQUE(namespace, k)
) STRICT;

CREATE TABLE tab_state (
  id TEXT PRIMARY KEY NOT NULL,
  data BLOB
) STRICT;

CREATE TABLE user (
  id BLOB PRIMARY KEY NOT NULL,
  email TEXT NOT NULL,
  joined_at INT NOT NULL,
  UNIQUE(email)
) STRICT;

CREATE TABLE todo (
  id BLOB PRIMARY KEY NOT NULL,
  archived INT NOT NULL,
  completed INT NOT NULL,
  created_at INT NOT NULL,
  title TEXT NOT NULL,
  updated_at INT NOT NULL,
  user_id BLOB NOT NULL,
  archived_at INT,
  FOREIGN KEY(user_id) REFERENCES user(id)
) STRICT;

CREATE INDEX idx_todo_archived ON todo(archived);
CREATE INDEX idx_todo_created_at ON todo(created_at);
CREATE INDEX idx_todo_updated_at ON todo(updated_at);
CREATE INDEX idx_todo_user_id ON todo(user_id);
CREATE INDEX idx_user_joined_at ON user(joined_at);