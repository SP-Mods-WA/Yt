import base64
import os
import sys

# 🔐 Secret Key (Java එකේ තියන එකම use කරන්න!)
SECRET_KEY = "YTProSecretKey2025!@#$%^&*()"

def xor_encrypt(data, key):
    """XOR encryption"""
    key_bytes = key.encode('utf-8')
    data_bytes = bytearray(data)
    
    for i in range(len(data_bytes)):
        data_bytes[i] ^= key_bytes[i % len(key_bytes)]
    
    return bytes(data_bytes)

def encrypt_file(input_file, output_file):
    """Encrypt file using XOR + Base64"""
    try:
        with open(input_file, 'rb') as f:
            original_data = f.read()
        
        print(f"📂 Reading {input_file} ({len(original_data)} bytes)")
        
        encrypted_data = xor_encrypt(original_data, SECRET_KEY)
        base64_encoded = base64.b64encode(encrypted_data).decode('utf-8')
        
        # Create output directory if it doesn't exist
        os.makedirs(os.path.dirname(output_file), exist_ok=True)
        
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(base64_encoded)
        
        print(f"✅ Encrypted: {output_file} ({len(base64_encoded)} chars)")
        return True
        
    except FileNotFoundError:
        print(f"❌ File not found: {input_file}")
        return False
    except Exception as e:
        print(f"❌ Error encrypting {input_file}: {e}")
        return False

def main():
    print("🔐 YTPro Assets Encryption")
    print("=" * 60)
    
    files = ['script.js', 'bgplay.js', 'innertube.js']
    success_count = 0
    
    for filename in files:
        input_path = f'assets/{filename}'
        output_path = f'app/src/main/assets/{filename}.enc'
        
        if os.path.exists(input_path):
            if encrypt_file(input_path, output_path):
                success_count += 1
        else:
            print(f"⚠️  {input_path} not found!")
    
    print("=" * 60)
    if success_count == len(files):
        print(f"🎉 Encryption complete! ({success_count}/{len(files)} files)")
        sys.exit(0)
    else:
        print(f"⚠️  Partial success: {success_count}/{len(files)} files")
        sys.exit(1)

if __name__ == "__main__":
    main()
