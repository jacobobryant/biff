fetch("/api/status", {
  headers: {
    'Accept': 'application/json'
  }
}).then(r => r.json())
  .then(data => {
  if (data['signed-in'] !== true) {
    document.location = "/";
  }
});
