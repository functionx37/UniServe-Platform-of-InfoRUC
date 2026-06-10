const fs = require('fs');
try {
    const content = fs.readFileSync('admin-frontend/src/App.tsx', 'utf-8');
    const lines = content.split('\n');
    let result = '';
    for(let i=0; i<lines.length; i++) {
        if(lines[i].includes('importUsers') || lines[i].includes('TabPane') || lines[i].includes('党团')) {
            result += `${i+1}: ${lines[i].trim()}\n`;
        }
    }
    fs.writeFileSync('extracted_lines2.txt', result);
} catch(e) {
    fs.writeFileSync('err2.txt', String(e));
}