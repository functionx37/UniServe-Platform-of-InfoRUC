const fs = require('fs');
const content = fs.readFileSync('admin-frontend/src/App.tsx', 'utf-8');
const lines = content.split('\n');
let res = [];
for(let i=0; i<lines.length; i++) {
  if (lines[i].includes('importUsers') || lines[i].includes('学生管理') || lines[i].includes('ImportUsers') || lines[i].includes('党团')) {
    res.push(`${i+1}: ${lines[i]}`);
  }
}
fs.writeFileSync('output.txt', res.join('\n'));