import os

base_dir = '/home/cheny/UniServe-Platform-of-InfoRUC'
app_path = os.path.join(base_dir, 'admin-frontend/src/App.tsx')

try:
    with open(app_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 1. 注入 import
    if 'PartyProgressImport' not in content:
        import_str = "import { PartyProgressImport } from './PartyProgressImport';\n"
        # 在最后一个 import 后面加
        last_import_idx = content.rfind('import ')
        if last_import_idx != -1:
            end_of_line = content.find('\n', last_import_idx)
            content = content[:end_of_line+1] + import_str + content[end_of_line+1:]
        else:
            content = import_str + content
            
    # 2. 注入组件，找一个比较适合的地方，例如用户管理的界面
    # 假设有个地方写了 <Button onClick={...import...} 或者 类似导入用户之类的
    if '<PartyProgressImport />' not in content:
        # 试着寻找导入用户的地方
        idx = content.find('导入用户')
        if idx != -1:
            # 回溯找到父标签或者直接加在后面
            # 安全起见，我们放在用户管理面板的某处，或者匹配 "// 导入" 附近
            start_div = content.rfind('<div', 0, idx)
            end_div = content.find('</div>', idx)
            if end_div != -1:
                content = content[:end_div] + '\n          <PartyProgressImport />\n        ' + content[end_div:]
        else:
            # 如果没找到导入用户，找个 TabPane 结尾处放进去
            tab_idx = content.find('tab="用户')
            if tab_idx != -1:
                end_pane = content.find('</TabPane>', tab_idx)
                if end_pane != -1:
                    content = content[:end_pane] + '\n        <PartyProgressImport />\n      ' + content[end_pane:]
            else:
                # 实在找不到，放在内容最下方
                last_div = content.rfind('</div>')
                content = content[:last_div] + '\n      <PartyProgressImport />\n    ' + content[last_div:]

    with open(app_path, 'w', encoding='utf-8') as f:
        f.write(content)
        
    with open(os.path.join(base_dir, 'inject_success.txt'), 'w') as f:
        f.write('Success! injected at ' + app_path)
except Exception as e:
    with open(os.path.join(base_dir, 'inject_error.txt'), 'w') as f:
        f.write(str(e))
