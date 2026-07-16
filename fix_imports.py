import os
import glob

def fix(path):
    with open(path, 'r') as f:
        lines = f.readlines()
    new_lines = []
    seen_R = False
    for line in lines:
        if 'com.tencent.fakegps.R' in line:
            line = line.replace('com.tencent.fakegps.R', 'com.github.fakegps.R')
        if 'import com.github.fakegps.R;' in line:
            if not seen_R:
                new_lines.append(line)
                seen_R = True
        else:
            new_lines.append(line)
    
    with open(path, 'w') as f:
        f.writelines(new_lines)

for p in glob.glob('app/src/main/java/**/*.java', recursive=True):
    fix(p)
