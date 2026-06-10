import os

try:
    with open('admin-frontend/src/App.tsx', 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    matches = []
    category_context = False
    for i, line in enumerate(lines):
        if '用户管理' in line or '学生管理' in line or '导入' in line or '<Tabs' in line or 'TabPane' in line or 'importUsers' in line:
            matches.append(f"{i}: {line.strip()}")
            
    with open('structure.txt', 'w', encoding='utf-8') as f:
        f.write('\n'.join(matches))
except Exception as e:
    with open('structure.txt', 'w', encoding='utf-8') as f:
        f.write(str(e))