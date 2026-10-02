path = "app/src/main/java/com/godot/devassistant/editor/GDScriptEditorView.kt"
with open(path, encoding="utf-8") as f:
    lines = f.readlines()

out = []
i = 0
while i < len(lines):
    ln = lines[i]
    # 1. 删除残留的注释行
    if "光标位置变化监听" in ln:
        i += 1
        continue
    # 2. 删除孤立的 invalidate() 及其后的 }
    if ln.strip() == "invalidate()" and i + 1 < len(lines) and lines[i + 1].strip() == "}":
        i += 2
        continue
    # 3. 在 customSelectionActionModeCallback 后补回被误删的函数头
    if "customSelectionActionModeCallback = object : ActionMode.Callback {" in ln:
        out.append(ln)
        out.append("            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {\n")
        out.append('                menu.add(0, 1, 0, "复制")\n')
        out.append('                menu.add(0, 2, 1, "粘贴")\n')
        i += 1
        continue
    out.append(ln)
    i += 1

with open(path, "w", encoding="utf-8") as f:
    f.writelines(out)
print("修复完成")
