import os
import re

def main():
    strings_xml_path = r'd:\Android_App\Bot_VK_Mobile\app\src\main\res\values\strings.xml'
    java_dir = r'd:\Android_App\Bot_VK_Mobile\app\src\main\java\com\vkbot\manager'
    res_dir = r'd:\Android_App\Bot_VK_Mobile\app\src\main\res'
    
    # Read existing strings
    with open(strings_xml_path, 'r', encoding='utf-8') as f:
        content = f.read()
        existing_strings = set(re.findall(r'name="([^"]+)"', content))
    
    # Scan for R.string references
    references = set()
    for root, _, files in os.walk(java_dir):
        for file in files:
            if file.endswith(('.kt', '.java')):
                with open(os.path.join(root, file), 'r', encoding='utf-8') as f:
                    file_content = f.read()
                    matches = re.findall(r'R\.string\.([a-z0-9_]+)', file_content)
                    references.update(matches)
    
    # Scan for @string references in XML files
    for root, dirs, files in os.walk(res_dir):
        if any(d in root for d in ['layout', 'menu', 'navigation', 'xml']):
            for file in files:
                if file.endswith('.xml'):
                    with open(os.path.join(root, file), 'r', encoding='utf-8') as f:
                        file_content = f.read()
                        matches = re.findall(r'@string/([a-z0-9_]+)', file_content)
                        references.update(matches)
    
    missing = sorted(list(references - existing_strings))
    if 'ok' in missing and 'android' in content: # android.R.string.ok is fine
        missing.remove('ok')
        
    print("Missing strings:")
    for m in missing:
        print(m)

if __name__ == "__main__":
    main()
