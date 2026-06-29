import re

with open('admin-frontend/src/App.tsx', 'r', encoding='utf-8') as f:
    lines = f.readlines()

output = []
# 提取所有相关的行（例如导入、状态、菜单、特定的管理员角色等信息）
patterns = [
    r'import', r'PartyProgressImport', r'党团', r'菜单', 
    r'menu', r'Nav', r'Sidebar', r'流程管理员', r'role',
    r'<PartyProgressImport'
]

for i, line in enumerate(lines):
    if any(re.search(pat, line, re.IGNORECASE) for pat in patterns):
        output.append(f"{i+1}: {line}")

# 为了避免结果太长，如果太长只截取前一部分
with open('app_analysis.txt', 'w', encoding='utf-8') as f:
    # 限制行数防止依然超大
    f.writelines(output[:800])