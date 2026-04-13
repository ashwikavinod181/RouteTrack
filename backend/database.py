"""
database.py - RouteTrack Database Models
"""

from sqlalchemy import create_engine, Column, Integer, String, Float, DateTime, Date, ForeignKey, Time, Boolean, Text
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session, relationship
from datetime import datetime, date
import os
from dotenv import load_dotenv

load_dotenv()

# ============= DATABASE SETUP =============
DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./routetrack.db")

engine = create_engine(
    DATABASE_URL,
    connect_args={"check_same_thread": False} if "sqlite" in DATABASE_URL else {},
    echo=False
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


# ============= DATABASE MODELS =============

class College(Base):
    __tablename__ = "colleges"
    
    college_id = Column(Integer, primary_key=True, index=True)
    college_name = Column(String(255), unique=True, index=True, nullable=False)
    college_email_domain = Column(String(255), unique=True, nullable=False)  # FIXED: matches main.py
    address = Column(String(500), nullable=True)
    phone = Column(String(20), nullable=True)  # FIXED: matches main.py
    created_at = Column(DateTime, default=datetime.utcnow)
    
    users = relationship("User", back_populates="college", cascade="all, delete-orphan")
    buses = relationship("Bus", back_populates="college", cascade="all, delete-orphan")
    stops = relationship("Stop", back_populates="college", cascade="all, delete-orphan")


class User(Base):
    __tablename__ = "users"
    
    user_id = Column(Integer, primary_key=True, index=True)
    username = Column(String(255), unique=True, index=True, nullable=False)
    email = Column(String(255), unique=True, index=True, nullable=False)
    password = Column(String(500), nullable=False)
    role = Column(String(20), nullable=False)
    college_id = Column(Integer, ForeignKey("colleges.college_id"), nullable=True)
    phone_number = Column(String(20), nullable=True)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    college = relationship("College", back_populates="users")
    driver_assignments = relationship("DriverAssignment", back_populates="user")


class Bus(Base):
    __tablename__ = "buses"
    
    bus_id = Column(Integer, primary_key=True, index=True)
    bus_number = Column(String(50), unique=True, index=True, nullable=False)
    route = Column(String(255), nullable=False)
    college_id = Column(Integer, ForeignKey("colleges.college_id"), nullable=False)
    capacity = Column(Integer, default=50)
    vehicle_type = Column(String(50), nullable=True)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    
    college = relationship("College", back_populates="buses")
    stops = relationship("Stop", back_populates="bus", cascade="all, delete-orphan")
    driver_assignments = relationship("DriverAssignment", back_populates="bus", cascade="all, delete-orphan")
    bus_statuses = relationship("BusStatus", back_populates="bus", cascade="all, delete-orphan")


class Driver(Base):
    __tablename__ = "drivers"
    
    driver_id = Column(Integer, primary_key=True, index=True)
    driver_name = Column(String(255), nullable=False)
    phone_number = Column(String(20), unique=True, nullable=False)
    email = Column(String(255), unique=True, nullable=True)
    license_number = Column(String(50), unique=True, nullable=False)
    license_expiry = Column(Date, nullable=True)
    address = Column(String(500), nullable=True)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    
    assignments = relationship("DriverAssignment", back_populates="driver", cascade="all, delete-orphan")


class Stop(Base):
    __tablename__ = "stops"
    
    stop_id = Column(Integer, primary_key=True, index=True)
    bus_id = Column(Integer, ForeignKey("buses.bus_id"), nullable=False)
    college_id = Column(Integer, ForeignKey("colleges.college_id"), nullable=False)
    stop_name = Column(String(255), nullable=False)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    scheduled_time = Column(Time, nullable=False)
    stop_order = Column(Integer, nullable=False)
    stop_type = Column(String(50), nullable=True)
    description = Column(Text, nullable=True)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    
    bus = relationship("Bus", back_populates="stops")
    college = relationship("College", back_populates="stops")
    bus_statuses = relationship("BusStatus", back_populates="stop", cascade="all, delete-orphan")


class DriverAssignment(Base):
    __tablename__ = "driver_assignment"
    
    assignment_id = Column(Integer, primary_key=True, index=True)
    driver_id = Column(Integer, ForeignKey("drivers.driver_id"), nullable=False)
    bus_id = Column(Integer, ForeignKey("buses.bus_id"), nullable=False)
    user_id = Column(Integer, ForeignKey("users.user_id"), nullable=True)
    assignment_date = Column(Date, nullable=False, default=date.today)
    start_time = Column(Time, nullable=True)
    end_time = Column(Time, nullable=True)
    notes = Column(Text, nullable=True)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    
    driver = relationship("Driver", back_populates="assignments")
    bus = relationship("Bus", back_populates="driver_assignments")
    user = relationship("User", back_populates="driver_assignments")


class BusStatus(Base):
    __tablename__ = "bus_status"
    
    status_id = Column(Integer, primary_key=True, index=True)
    bus_id = Column(Integer, ForeignKey("buses.bus_id"), nullable=False)
    stop_id = Column(Integer, ForeignKey("stops.stop_id"), nullable=True)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    actual_time = Column(DateTime, default=datetime.utcnow)
    scheduled_time = Column(Time, nullable=True)
    delay_minutes = Column(Integer, default=0)
    is_current = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    
    bus = relationship("Bus", back_populates="bus_statuses")
    stop = relationship("Stop", back_populates="bus_statuses")


class AdminUser(Base):
    __tablename__ = "admin_users"
    
    admin_id = Column(Integer, primary_key=True, index=True)
    username = Column(String(255), unique=True, index=True, nullable=False)
    email = Column(String(255), unique=True, index=True, nullable=False)
    password = Column(String(500), nullable=False)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)


# ============= DATABASE FUNCTIONS =============

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_db():
    Base.metadata.create_all(bind=engine)


init_db()
