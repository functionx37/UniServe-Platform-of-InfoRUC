const fs = require('fs');
const path = require('path');
try {
    const content = fs.readFileSync(path.join(__dirname, 'admin-frontend/src/App.tsx'), 'utf-8');
    const lines = content.split('\n');
    const size = 500;
    for(let i=0; i<Math.ceil(lines.length/size); i++) {
        fs.writeFileSync(path.join(__dirname, `app_part_${i}.txt`), lines.slice(i*size, (i+1)*size).join('\n'));
    }
} catch(e) {
    console.error(e);
}