// Creates atunwa.config.json (git-ignored) from the example the first time you install, so the
// app builds. It then shows a "add your key" screen until you fill the file in.
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const config = path.join(root, 'atunwa.config.json');
if (!fs.existsSync(config)) {
  fs.copyFileSync(path.join(root, 'atunwa.config.example.json'), config);
  console.log('Created atunwa.config.json. Add your Atunwa key to it.');
}
