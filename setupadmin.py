import os
import sys
from pathlib import Path
from datetime import datetime

sys.path.insert(0, str(Path(__file__).parent))

from database import SessionLocal, AdminUser, Base, engine

# FIX: Use bcrypt directly — same as main.py
# Old version used passlib which is incompatible with bcrypt 4.x
import bcrypt as _bcrypt

def hash_password(password: str) -> str:
    return _bcrypt.hashpw(password.encode("utf-8"), _bcrypt.gensalt()).decode("utf-8")

Base.metadata.create_all(bind=engine)

db = SessionLocal()

try:
    admin_exists = db.query(AdminUser).filter(
        AdminUser.username == "admin"
    ).first()
    
    if admin_exists:
        print("⚠️  Admin user already exists!")
        print(f"   Username: {admin_exists.username}")
        print(f"   Email: {admin_exists.email}")
    else:
        admin = AdminUser(
            username="admin",
            email="admin@routetrack.local",
            password=hash_password("admin123"),
            is_active=True,
            created_at=datetime.utcnow()
        )
        
        db.add(admin)
        db.commit()
        db.refresh(admin)
        
        print("=" * 50)
        print("✅ Admin User Created Successfully!")
        print("=" * 50)
        print(f"Admin ID  : {admin.admin_id}")
        print(f"Username  : admin")
        print(f"Email     : admin@routetrack.local")
        print(f"Password  : admin123 (hashed in database)")
        print("=" * 50)
        print("\n📝 LOGIN CREDENTIALS:")
        print("   Username: admin")
        print("   Password: admin123")
        print("\n🚀 Next: Run backend with:")
        print("   uvicorn main:app --reload --host 0.0.0.0 --port 8000")
        print("=" * 50)
        
except Exception as e:
    db.rollback()
    print(f"❌ Error creating admin user: {e}")
    print("\n📋 If admin already exists, you can:")
    print("   1. Use existing credentials (admin/admin123)")
    print("   2. Delete routetrack.db and try again")
    
finally:
    db.close()