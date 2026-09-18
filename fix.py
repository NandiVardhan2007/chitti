import sys

with open(r'C:\Users\ramuv\chitti\app\src\main\java\com\owlcoders\chitti\MainActivity.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

start_idx = -1
end_idx = -1

for i, line in enumerate(lines):
    if 'Text("Explore App (Preview Mode)")' in line:
        start_idx = i + 1
        break

for i, line in enumerate(lines):
    if '} // End Crossfade' in line:
        end_idx = i
        break

if start_idx != -1 and end_idx != -1:
    new_lines = lines[:start_idx] + ['                            }\n                        }\n                    }\n'] + lines[end_idx:]
    with open(r'C:\Users\ramuv\chitti\app\src\main\java\com\owlcoders\chitti\MainActivity.kt', 'w', encoding='utf-8') as f:
        f.writelines(new_lines)
    print("Fixed MainActivity.kt")
else:
    print(f"Could not find markers. start_idx: {start_idx}, end_idx: {end_idx}")
