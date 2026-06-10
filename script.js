const fs = require('fs');
const file = 'admin-frontend/src/App.tsx';
const text = fs.readFileSync(file, 'utf-8');
const lines = text.split('\n');
fs.writeFileSync('output1.txt', lines.slice(0, 400).join('\n'));
fs.writeFileSync('output2.txt', lines.slice(400, 800).join('\n'));
fs.writeFileSync('output3.txt', lines.slice(800, 1200).join('\n'));
fs.writeFileSync('output4.txt', lines.slice(1200).join('\n'));
console.log('done');