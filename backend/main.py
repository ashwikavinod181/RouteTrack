from fastapi import FastAPI, Depends, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
from sqlalchemy import func
from datetime import datetime, timedelta, date, time as time_type
import logging
import os
from dotenv import load_dotenv
from jose import JWTError, jwt
from typing import Optional
from database import (
    SessionLocal, engine, Base,
    College, User, Bus, Driver, Stop, DriverAssignment, BusStatus, AdminUser
)

load_dotenv()

SECRET_KEY = os.getenv("SECRET_KEY", "your-secret-key-change-this")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60 * 24

Base.metadata.create_all(bind=engine)

app = FastAPI(title="RouteTrack API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

import bcrypt as _bcrypt

def hash_password(password: str) -> str:
    return _bcrypt.hashpw(
        password.encode("utf-8"), _bcrypt.gensalt()
    ).decode("utf-8")

def verify_password(plain_password: str, hashed_password: str) -> bool:
    try:
        return _bcrypt.checkpw(
            plain_password.encode("utf-8"),
            hashed_password.encode("utf-8")
        )
    except Exception as e:
        logger.error(f"Password verification error: {e}")
        return False

from pydantic import BaseModel

class LoginRequest(BaseModel):
    username: str
    password: str
    role: str

class SignupRequest(BaseModel):
    username: str
    email: str
    password: str
    role: str
    phone_number: Optional[str] = None

class TokenResponse(BaseModel):
    access_token: str
    token_type: str
    user_id: Optional[int] = None
    role: str
    username: str
    success: bool = True
    message: Optional[str] = None
    user: Optional[dict] = None

class BusDetailResponse(BaseModel):
    bus_id: int
    bus_number: str
    route: str
    driver_name: str
    next_stop: Optional[str]
    next_stop_time: Optional[str]
    current_latitude: float
    current_longitude: float
    delay_minutes: int
    status: str

class BusesResponse(BaseModel):
    success: bool
    buses: list

class DriverLocationRequest(BaseModel):
    bus_number: str
    latitude: float
    longitude: float
    is_trip_active: bool

# ===================== DATABASE DEPENDENCY =====================

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

# ===================== UTILITY FUNCTIONS =====================

def create_access_token(user_id: int, role: str, expires_delta: Optional[timedelta] = None):
    to_encode = {"sub": str(user_id), "role": role}
    expire = datetime.utcnow() + (expires_delta if expires_delta else timedelta(hours=24))
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)

def parse_time(raw) -> Optional[time_type]:
    if raw is None:
        return None
    if isinstance(raw, time_type):
        return raw
    if isinstance(raw, str) and raw.strip():
        try:
            parts = raw.strip().split(":")
            hour   = int(parts[0])
            minute = int(parts[1]) if len(parts) > 1 else 0
            second = int(parts[2]) if len(parts) > 2 else 0
            return time_type(hour, minute, second)
        except Exception as e:
            logger.error(f"Time parse error for value '{raw}': {e}")
            raise ValueError(f"Invalid time format: '{raw}'. Use HH:MM or HH:MM:SS")
    return None

# ===================== STARTUP: SEED DEFAULT COLLEGES =====================

@app.on_event("startup")
def seed_default_colleges():
    db = SessionLocal()
    try:
        default_colleges = [
            {
                "college_name": "Providence College of Engineering",
                "college_email_domain": "student.providence.edu.in",
                "phone": "0000000000"
            },
            {
                "college_name": "Providence College",
                "college_email_domain": "providence.edu.in",
                "phone": "0000000000"
            },
        ]
        for col in default_colleges:
            exists = db.query(College).filter(
                College.college_email_domain == col["college_email_domain"]
            ).first()
            if not exists:
                db.add(College(
                    college_name=col["college_name"],
                    college_email_domain=col["college_email_domain"],
                    phone=col["phone"]
                ))
                logger.info(f"Seeded college: {col['college_name']}")
        db.commit()
    except Exception as e:
        logger.error(f"Seed college error: {e}")
        db.rollback()
    finally:
        db.close()

# ===================== AUTH ENDPOINTS =====================

@app.post("/api/auth/login")
def login(request: LoginRequest, db: Session = Depends(get_db)):
    try:
        if request.role not in ["student", "driver"]:
            return {
                "success": False,
                "message": "Role must be 'student' or 'driver'",
                "access_token": "", "token_type": "bearer",
                "role": request.role, "username": ""
            }

        input_value = request.username.strip().lower()

        user = db.query(User).filter(
            User.username == input_value,
            User.role == request.role
        ).first()

        if not user:
            user = db.query(User).filter(
                User.email == input_value,
                User.role == request.role
            ).first()

        if not user:
            return {
                "success": False,
                "message": "No account found with that email. Please sign up first.",
                "access_token": "", "token_type": "bearer",
                "role": request.role, "username": ""
            }

        if not verify_password(request.password, user.password):
            return {
                "success": False,
                "message": "Wrong password. Please try again.",
                "access_token": "", "token_type": "bearer",
                "role": request.role, "username": ""
            }

        token = create_access_token(user.user_id, request.role)
        return {
            "success": True,
            "message": "Login successful",
            "access_token": token,
            "token_type": "bearer",
            "user_id": user.user_id,
            "role": user.role,
            "username": user.username,
            "user": {
                "user_id": user.user_id,
                "username": user.username,
                "email": user.email,
                "role": user.role,
                "phone_number": user.phone_number
            }
        }

    except Exception as e:
        logger.error(f"Login error: {e}")
        return {
            "success": False,
            "message": "Server error. Please try again.",
            "access_token": "", "token_type": "bearer",
            "role": "", "username": ""
        }

@app.post("/api/auth/signup")
def signup(request: SignupRequest, db: Session = Depends(get_db)):
    try:
        if request.role not in ["student", "driver"]:
            return {
                "success": False,
                "message": "Role must be 'student' or 'driver'",
                "access_token": "", "token_type": "bearer",
                "role": request.role, "username": ""
            }

        if db.query(User).filter(
            User.username == request.username.strip().lower()
        ).first():
            return {
                "success": False,
                "message": "Username already exists. Please choose another.",
                "access_token": "", "token_type": "bearer",
                "role": request.role, "username": ""
            }

        if db.query(User).filter(
            User.email == request.email.strip().lower()
        ).first():
            return {
                "success": False,
                "message": "This email is already registered. Please login instead.",
                "access_token": "", "token_type": "bearer",
                "role": request.role, "username": ""
            }

        if request.role == "student":
            if "@" not in request.email:
                return {
                    "success": False,
                    "message": "Please enter a valid email address.",
                    "access_token": "", "token_type": "bearer",
                    "role": request.role, "username": ""
                }

            email_domain = request.email.strip().lower().split("@")[1]
            college = db.query(College).filter(
                College.college_email_domain == email_domain
            ).first()

            if not college:
                return {
                    "success": False,
                    "message": (
                        f"Email domain '@{email_domain}' is not registered. "
                        f"Please use your official college email."
                    ),
                    "access_token": "", "token_type": "bearer",
                    "role": request.role, "username": ""
                }

        new_user = User(
            username=request.username.strip().lower(),
            email=request.email.strip().lower(),
            password=hash_password(request.password),
            role=request.role,
            phone_number=request.phone_number
        )
        db.add(new_user)
        db.commit()
        db.refresh(new_user)

        token = create_access_token(new_user.user_id, request.role)
        return {
            "success": True,
            "message": "Account created successfully",
            "access_token": token,
            "token_type": "bearer",
            "user_id": new_user.user_id,
            "role": new_user.role,
            "username": new_user.username,
            "user": {
                "user_id": new_user.user_id,
                "username": new_user.username,
                "email": new_user.email,
                "role": new_user.role,
                "phone_number": new_user.phone_number
            }
        }

    except Exception as e:
        logger.error(f"Signup error: {e}")
        db.rollback()
        return {
            "success": False,
            "message": "Server error. Please try again.",
            "access_token": "", "token_type": "bearer",
            "role": "", "username": ""
        }

# ============= ADMIN AUTH ENDPOINT =============

@app.post("/api/auth/admin-login")
def admin_login(request: LoginRequest, db: Session = Depends(get_db)):
    try:
        if request.role != "admin":
            raise HTTPException(status_code=422, detail="Role must be 'admin'")

        admin = db.query(AdminUser).filter(
            AdminUser.username == request.username.strip().lower()
        ).first()

        if not admin or not verify_password(request.password, admin.password):
            raise HTTPException(status_code=401, detail="Invalid admin credentials")

        token = create_access_token(admin.admin_id, "admin")
        return {
            "success": True,
            "access_token": token,
            "token_type": "bearer",
            "user_id": admin.admin_id,
            "role": "admin",
            "username": admin.username
        }

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Admin login error: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")

# ===================== COLLEGE ENDPOINTS =====================

@app.get("/api/admin/colleges")
def get_colleges(db: Session = Depends(get_db)):
    try:
        colleges = db.query(College).all()
        return {
            "success": True,
            "colleges": [
                {
                    "college_id": c.college_id,
                    "college_name": c.college_name,
                    "email_domain": c.college_email_domain,
                    "college_email_domain": c.college_email_domain,
                    "phone": c.phone or "-",
                    "phone_number": c.phone or "-"
                }
                for c in colleges
            ]
        }
    except Exception as e:
        logger.error(f"Get colleges error: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")

@app.post("/api/admin/colleges")
def create_college(college_data: dict, db: Session = Depends(get_db)):
    try:
        college_name   = college_data.get("college_name", "").strip()
        email_domain   = (
            college_data.get("college_email_domain") or
            college_data.get("email_domain") or ""
        ).strip()
        phone = (
            college_data.get("phone") or
            college_data.get("phone_number") or ""
        ).strip()

        # FIX: check for duplicate college name case-insensitively
        # This prevents the UNIQUE constraint error when the admin tries
        # to add a college that was already seeded at startup with different casing
        existing_name = db.query(College).filter(
            func.lower(College.college_name) == college_name.lower()
        ).first()
        if existing_name:
            return {
                "success": False,
                "message": f"College '{college_name}' already exists."
            }

        # Also check for duplicate email domain
        existing_domain = db.query(College).filter(
            College.college_email_domain == email_domain
        ).first()
        if existing_domain:
            return {
                "success": False,
                "message": f"Email domain '{email_domain}' is already registered to '{existing_domain.college_name}'."
            }

        new_college = College(
            college_name=college_name,
            college_email_domain=email_domain,
            phone=phone or None
        )
        db.add(new_college)
        db.commit()
        db.refresh(new_college)
        return {"success": True, "message": "College created", "college_id": new_college.college_id}
    except Exception as e:
        logger.error(f"Create college error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))

@app.put("/api/admin/colleges/{college_id}")
def update_college(college_id: int, college_data: dict, db: Session = Depends(get_db)):
    try:
        college = db.query(College).filter(College.college_id == college_id).first()
        if not college:
            raise HTTPException(status_code=404, detail="College not found")
        college.college_name = college_data.get("college_name", college.college_name)
        college.college_email_domain = (
            college_data.get("college_email_domain") or
            college_data.get("email_domain") or
            college.college_email_domain
        )
        college.phone = (
            college_data.get("phone") or
            college_data.get("phone_number") or
            college.phone
        )
        db.commit()
        return {"success": True, "message": "College updated"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Update college error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail="Internal server error")

@app.delete("/api/admin/colleges/{college_id}")
def delete_college(college_id: int, db: Session = Depends(get_db)):
    try:
        college = db.query(College).filter(College.college_id == college_id).first()
        if not college:
            raise HTTPException(status_code=404, detail="College not found")
        db.delete(college)
        db.commit()
        return {"success": True, "message": "College deleted"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Delete college error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail="Internal server error")

# ===================== BUS ENDPOINTS =====================

@app.get("/api/admin/buses")
def get_buses(db: Session = Depends(get_db)):
    try:
        buses = db.query(Bus).all()
        return {
            "success": True,
            "buses": [
                {
                    "bus_id": b.bus_id,
                    "bus_number": b.bus_number,
                    "route": b.route,
                    "college_id": b.college_id,
                    "capacity": b.capacity,
                    "is_active": b.is_active
                }
                for b in buses
            ]
        }
    except Exception as e:
        logger.error(f"Get buses error: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")

@app.post("/api/admin/buses")
def create_bus(bus_data: dict, db: Session = Depends(get_db)):
    try:
        new_bus = Bus(
            bus_number=bus_data.get("bus_number"),
            route=bus_data.get("route"),
            college_id=bus_data.get("college_id"),
            capacity=bus_data.get("capacity", 50)
        )
        db.add(new_bus)
        db.commit()
        db.refresh(new_bus)
        return {"success": True, "message": "Bus created", "bus_id": new_bus.bus_id}
    except Exception as e:
        logger.error(f"Create bus error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))

@app.put("/api/admin/buses/{bus_id}")
def update_bus(bus_id: int, bus_data: dict, db: Session = Depends(get_db)):
    try:
        bus = db.query(Bus).filter(Bus.bus_id == bus_id).first()
        if not bus:
            raise HTTPException(status_code=404, detail="Bus not found")
        bus.bus_number = bus_data.get("bus_number", bus.bus_number)
        bus.route      = bus_data.get("route", bus.route)
        bus.college_id = bus_data.get("college_id", bus.college_id)
        bus.capacity   = bus_data.get("capacity", bus.capacity)
        db.commit()
        return {"success": True, "message": "Bus updated"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Update bus error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail="Internal server error")

@app.delete("/api/admin/buses/{bus_id}")
def delete_bus(bus_id: int, db: Session = Depends(get_db)):
    try:
        bus = db.query(Bus).filter(Bus.bus_id == bus_id).first()
        if not bus:
            raise HTTPException(status_code=404, detail="Bus not found")
        db.delete(bus)
        db.commit()
        return {"success": True, "message": "Bus deleted"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Delete bus error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail="Internal server error")

# ===================== DRIVER ENDPOINTS =====================

@app.get("/api/admin/drivers")
def get_drivers(db: Session = Depends(get_db)):
    try:
        drivers = db.query(Driver).all()
        return {
            "success": True,
            "drivers": [
                {
                    "driver_id": d.driver_id,
                    "driver_name": d.driver_name,
                    "phone_number": d.phone_number,
                    "email": d.email or "-",
                    "license_number": d.license_number,
                    "is_active": d.is_active
                }
                for d in drivers
            ]
        }
    except Exception as e:
        logger.error(f"Get drivers error: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")

@app.post("/api/admin/drivers")
def create_driver(driver_data: dict, db: Session = Depends(get_db)):
    try:
        new_driver = Driver(
            driver_name=driver_data.get("driver_name"),
            phone_number=driver_data.get("phone_number"),
            email=driver_data.get("email"),
            license_number=driver_data.get("license_number")
        )
        db.add(new_driver)
        db.commit()
        db.refresh(new_driver)
        return {"success": True, "message": "Driver created", "driver_id": new_driver.driver_id}
    except Exception as e:
        logger.error(f"Create driver error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))

@app.put("/api/admin/drivers/{driver_id}")
def update_driver(driver_id: int, driver_data: dict, db: Session = Depends(get_db)):
    try:
        driver = db.query(Driver).filter(Driver.driver_id == driver_id).first()
        if not driver:
            raise HTTPException(status_code=404, detail="Driver not found")
        driver.driver_name    = driver_data.get("driver_name", driver.driver_name)
        driver.phone_number   = driver_data.get("phone_number", driver.phone_number)
        driver.email          = driver_data.get("email", driver.email)
        driver.license_number = driver_data.get("license_number", driver.license_number)
        db.commit()
        return {"success": True, "message": "Driver updated"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Update driver error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail="Internal server error")

@app.delete("/api/admin/drivers/{driver_id}")
def delete_driver(driver_id: int, db: Session = Depends(get_db)):
    try:
        driver = db.query(Driver).filter(Driver.driver_id == driver_id).first()
        if not driver:
            raise HTTPException(status_code=404, detail="Driver not found")
        db.delete(driver)
        db.commit()
        return {"success": True, "message": "Driver deleted"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Delete driver error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail="Internal server error")

# ===================== STOP ENDPOINTS =====================

@app.get("/api/admin/stops")
def get_stops(db: Session = Depends(get_db)):
    try:
        stops = db.query(Stop).all()
        return {
            "success": True,
            "stops": [
                {
                    "stop_id": s.stop_id,
                    "bus_id": s.bus_id,
                    "college_id": s.college_id,
                    "stop_name": s.stop_name,
                    "latitude": s.latitude,
                    "longitude": s.longitude,
                    "scheduled_time": str(s.scheduled_time) if s.scheduled_time else None,
                    "stop_order": s.stop_order
                }
                for s in stops
            ]
        }
    except Exception as e:
        logger.error(f"Get stops error: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")

@app.post("/api/admin/stops")
def create_stop(stop_data: dict, db: Session = Depends(get_db)):
    try:
        college_id = stop_data.get("college_id")
        if not college_id:
            bus = db.query(Bus).filter(Bus.bus_id == stop_data.get("bus_id")).first()
            college_id = bus.college_id if bus else 1
        scheduled_time = parse_time(stop_data.get("scheduled_time"))
        new_stop = Stop(
            bus_id=stop_data.get("bus_id"),
            college_id=college_id,
            stop_name=stop_data.get("stop_name"),
            latitude=stop_data.get("latitude"),
            longitude=stop_data.get("longitude"),
            scheduled_time=scheduled_time,
            stop_order=stop_data.get("stop_order")
        )
        db.add(new_stop)
        db.commit()
        db.refresh(new_stop)
        return {"success": True, "message": "Stop created", "stop_id": new_stop.stop_id}
    except Exception as e:
        logger.error(f"Create stop error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))

@app.put("/api/admin/stops/{stop_id}")
def update_stop(stop_id: int, stop_data: dict, db: Session = Depends(get_db)):
    try:
        stop = db.query(Stop).filter(Stop.stop_id == stop_id).first()
        if not stop:
            raise HTTPException(status_code=404, detail="Stop not found")
        stop.bus_id     = stop_data.get("bus_id", stop.bus_id)
        stop.stop_name  = stop_data.get("stop_name", stop.stop_name)
        stop.latitude   = stop_data.get("latitude", stop.latitude)
        stop.longitude  = stop_data.get("longitude", stop.longitude)
        if "scheduled_time" in stop_data:
            stop.scheduled_time = parse_time(stop_data.get("scheduled_time"))
        stop.stop_order = stop_data.get("stop_order", stop.stop_order)
        db.commit()
        return {"success": True, "message": "Stop updated"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Update stop error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail="Internal server error")

@app.delete("/api/admin/stops/{stop_id}")
def delete_stop(stop_id: int, db: Session = Depends(get_db)):
    try:
        stop = db.query(Stop).filter(Stop.stop_id == stop_id).first()
        if not stop:
            raise HTTPException(status_code=404, detail="Stop not found")
        db.delete(stop)
        db.commit()
        return {"success": True, "message": "Stop deleted"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Delete stop error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail="Internal server error")

# ===================== ASSIGNMENT ENDPOINTS =====================

@app.get("/api/admin/assignments")
def get_assignments(db: Session = Depends(get_db)):
    try:
        assignments = db.query(DriverAssignment).all()
        return {
            "success": True,
            "assignments": [
                {
                    "assignment_id": a.assignment_id,
                    "driver_id": a.driver_id,
                    "bus_id": a.bus_id,
                    "driver_name": a.driver.driver_name if a.driver else "Unknown",
                    "bus_number": a.bus.bus_number if a.bus else "Unknown",
                    "start_time": str(a.start_time) if a.start_time else None,
                    "end_time": str(a.end_time) if a.end_time else None,
                    "assignment_date": str(a.assignment_date) if a.assignment_date else None
                }
                for a in assignments
            ]
        }
    except Exception as e:
        logger.error(f"Get assignments error: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")

@app.post("/api/admin/assignments")
def create_assignment(assignment_data: dict, db: Session = Depends(get_db)):
    try:
        new_assignment = DriverAssignment(
            driver_id=assignment_data.get("driver_id"),
            bus_id=assignment_data.get("bus_id"),
            assignment_date=date.today(),
            start_time=parse_time(assignment_data.get("start_time")),
            end_time=parse_time(assignment_data.get("end_time"))
        )
        db.add(new_assignment)
        db.commit()
        db.refresh(new_assignment)
        return {"success": True, "message": "Assignment created", "assignment_id": new_assignment.assignment_id}
    except Exception as e:
        logger.error(f"Create assignment error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))

@app.delete("/api/admin/assignments/{assignment_id}")
def delete_assignment(assignment_id: int, db: Session = Depends(get_db)):
    try:
        assignment = db.query(DriverAssignment).filter(
            DriverAssignment.assignment_id == assignment_id
        ).first()
        if not assignment:
            raise HTTPException(status_code=404, detail="Assignment not found")
        db.delete(assignment)
        db.commit()
        return {"success": True, "message": "Assignment deleted"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Delete assignment error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail="Internal server error")

# ===================== STUDENT ENDPOINTS =====================

@app.get("/api/admin/students")
def get_students(db: Session = Depends(get_db)):
    try:
        students = db.query(User).filter(User.role == "student").all()
        return {
            "success": True,
            "students": [
                {
                    "user_id": s.user_id,
                    "username": s.username,
                    "email": s.email,
                    "phone_number": s.phone_number or "-",
                    "role": s.role,
                    "is_active": s.is_active,
                    "created_at": str(s.created_at) if s.created_at else None,
                    "college_name": s.college.college_name if s.college else None
                }
                for s in students
            ]
        }
    except Exception as e:
        logger.error(f"Get students error: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")

@app.put("/api/admin/students/{student_id}/toggle")
def toggle_student(student_id: int, db: Session = Depends(get_db)):
    try:
        student = db.query(User).filter(User.user_id == student_id).first()
        if not student:
            raise HTTPException(status_code=404, detail="Student not found")
        student.is_active = not student.is_active
        db.commit()
        return {"success": True, "message": "Student status updated"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Toggle student error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail="Internal server error")

@app.delete("/api/admin/students/{student_id}")
def delete_student(student_id: int, db: Session = Depends(get_db)):
    try:
        student = db.query(User).filter(User.user_id == student_id).first()
        if not student:
            raise HTTPException(status_code=404, detail="Student not found")
        db.delete(student)
        db.commit()
        return {"success": True, "message": "Student deleted"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Delete student error: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail="Internal server error")

# ===================== DASHBOARD ENDPOINTS =====================

@app.get("/api/admin/dashboard/stats")
def get_dashboard_stats(db: Session = Depends(get_db)):
    try:
        total_colleges = db.query(func.count(College.college_id)).scalar()
        total_buses    = db.query(func.count(Bus.bus_id)).scalar()
        active_buses   = db.query(func.count(Bus.bus_id)).filter(Bus.is_active == True).scalar()
        total_drivers  = db.query(func.count(Driver.driver_id)).scalar()
        total_stops    = db.query(func.count(Stop.stop_id)).scalar()
        total_students = db.query(func.count(User.user_id)).filter(User.role == "student").scalar()
        return {
            "success": True,
            "stats": {
                "total_colleges": total_colleges or 0,
                "total_buses":    total_buses    or 0,
                "active_buses":   active_buses   or 0,
                "total_drivers":  total_drivers  or 0,
                "total_stops":    total_stops    or 0,
                "total_students": total_students or 0
            }
        }
    except Exception as e:
        logger.error(f"Get stats error: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")

# ===================== PUBLIC BUS SEARCH ENDPOINT =====================

@app.get("/api/buses/search")
def search_buses(db: Session = Depends(get_db)):
    try:
        buses = db.query(Bus).all()
        bus_details = []
        for bus in buses:
            assignment = db.query(DriverAssignment).filter(
                DriverAssignment.bus_id == bus.bus_id,
                DriverAssignment.is_active == True
            ).first()
            driver_name = "Unknown"
            if assignment and assignment.driver:
                driver_name = assignment.driver.driver_name
            next_stop = db.query(Stop).filter(
                Stop.bus_id == bus.bus_id
            ).order_by(Stop.stop_order).first()
            bus_status = db.query(BusStatus).filter(
                BusStatus.bus_id == bus.bus_id,
                BusStatus.is_current == True
            ).first()
            delay = bus_status.delay_minutes if bus_status else 0
            status_display = "On Time" if delay == 0 else (f"{delay} mins late" if delay > 0 else "Early")
            status_color   = "Warning" if delay > 0 else "Success"
            LOCATION_STALE_SECONDS = 120
            is_active = bool(
                bus_status and bus_status.actual_time and
                (datetime.utcnow() - bus_status.actual_time).total_seconds() < LOCATION_STALE_SECONDS
            )
            bus_details.append({
                "bus_id":            bus.bus_id,
                "bus_number":        bus.bus_number,
                "route":             bus.route,
                "routeName":         bus.route,
                "routeDescription":  f"Driver: {driver_name}",
                "driver_name":       driver_name,
                "next_stop":         next_stop.stop_name          if next_stop else None,
                "next_stop_time":    str(next_stop.scheduled_time) if next_stop else None,
                "scheduledTime":     str(next_stop.scheduled_time) if next_stop else "N/A",
                "expectedTime":      str(next_stop.scheduled_time) if next_stop else "N/A",
                "current_latitude":  bus_status.latitude  if bus_status else 0.0,
                "current_longitude": bus_status.longitude if bus_status else 0.0,
                "delay_minutes":     delay,
                "status":            status_display,
                "statusColor":       status_color,
                "is_trip_active":    is_active
            })
        return {"success": True, "buses": bus_details}
    except Exception as e:
        logger.error(f"Search buses error: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")

# ===================== DRIVER LOCATION ENDPOINT =====================

@app.post("/api/driver/location")
def update_driver_location(request: DriverLocationRequest, db: Session = Depends(get_db)):
    try:
        bus = db.query(Bus).filter(
            func.upper(Bus.bus_number) == request.bus_number.strip().upper()
        ).first()
        if not bus:
            logger.warning(f"Driver location update: bus '{request.bus_number}' not found")
            return {
                "success": False,
                "message": f"Bus '{request.bus_number}' not found. Check the bus number and try again."
            }
        db.query(BusStatus).filter(
            BusStatus.bus_id == bus.bus_id,
            BusStatus.is_current == True
        ).update({"is_current": False})
        cutoff = datetime.utcnow() - timedelta(minutes=10)
        db.query(BusStatus).filter(
            BusStatus.bus_id == bus.bus_id,
            BusStatus.created_at < cutoff
        ).delete()
        if request.latitude != 0.0 and request.longitude != 0.0:
            db.add(BusStatus(
                bus_id=bus.bus_id,
                latitude=request.latitude,
                longitude=request.longitude,
                actual_time=datetime.utcnow(),
                delay_minutes=0,
                is_current=True
            ))
        db.commit()
        return {"success": True, "message": "Location updated successfully"}
    except Exception as e:
        logger.error(f"Driver location update error: {e}")
        db.rollback()
        return {"success": False, "message": str(e)}

# ===================== HEALTH CHECK =====================

@app.get("/api/health")
def health_check():
    return {
        "status": "operational",
        "version": "1.0.0",
        "timestamp": datetime.utcnow().isoformat()
    }

@app.get("/")
def root():
    return {
        "message": "RouteTrack API is running",
        "docs": "/docs",
        "version": "1.0.0"
    }
