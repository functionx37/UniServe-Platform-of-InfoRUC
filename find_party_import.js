"const fs = require('fs');
const content = fs.readFileSync('admin-frontend/src/App.tsx', 'utf-8');
const lines = content.split('\\n');
let output = '';
lines.forEach((line, index) => {
  if (line.match(/PartyProgressImport|import |党团|menu|nav|sidebar|role|流程/i)) {
    output += `${index + 1}: ${line}\\n`;
  }
});
fs.writeFileSync('target_info.txt', output.slice(0, 10000));
"