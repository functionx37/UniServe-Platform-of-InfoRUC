const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, 'admin-frontend/src/App.tsx');
let code = fs.readFileSync(file, 'utf-8');

if (!code.includes('importPartyProgress')) {
    code = code.replace('importUsers(rows)', 'importUsers(rows)\n        //...');
    const btnStr = `onClick={() => setImportUsersModalVisible(true)}`;
    if (code.includes(btnStr)) {
        code = code.replace(
            btnStr,
            `onClick={() => setImportUsersModalVisible(true)}\n          {/* 插入区域 */}\n          <button onClick={() => setImportPartyProgressVisible(true)}>导入党团进度</button>`
        )
    }
    fs.writeFileSync(file, code);
}