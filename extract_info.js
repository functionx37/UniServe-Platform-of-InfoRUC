const fs = require('fs');
const path = require('path');
try {
    const content = fs.readFileSync(path.join(__dirname, 'admin-frontend/src/App.tsx'), 'utf-8');
    const lines = content.split('\n');
    let result = '';
    for(let i=0; i<lines.length; i++) {
        if(lines[i].includes('上传') || lines[i].includes('importUsers') || lines[i].includes('importNotifications') || lines[i].includes('管理')) {
            result += `${i+1}: ${lines[i].trim()}\n`;
        }
    }
    fs.writeFileSync(path.join(__dirname, 'extracted_lines.txt'), result);
} catch(e) {
    fs.writeFileSync(path.join(__dirname, 'err.txt'), String(e));
}