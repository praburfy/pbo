package com.example.ta.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.example.ta.model.Booking;
import com.example.ta.model.Room;
import com.example.ta.model.RoomType;
import com.example.ta.model.User;
import com.example.ta.repository.BookingRepository;
import com.example.ta.repository.RoomRepository;
import com.example.ta.repository.RoomTypeRepository;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/user")
public class UserBookingController {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private BookingRepository bookingRepository;

    // Home page - tampilkan room types
    @GetMapping("/home")
    public String home(
            @RequestParam(required = false) LocalDate checkIn,
            @RequestParam(required = false) LocalDate checkOut,
            @RequestParam(required = false) Long roomTypeId,
            Model model,
            HttpSession session) {

        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";

        List<Room> availableRooms;

        if (checkIn != null && checkOut != null) {
            if (checkIn.isEqual(checkOut) || checkIn.isAfter(checkOut)) {
                model.addAttribute("errorMessage", "Tanggal Check-out harus setelah Tanggal Check-in.");
                availableRooms = new ArrayList<>();
            } else {
                if (roomTypeId != null) {
                    RoomType roomType = roomTypeRepository.findById(roomTypeId).orElse(null);
                    availableRooms = roomRepository.findAvailableRoomsByTypeAndDateRange(roomType, checkIn, checkOut);
                } else {
                    availableRooms = roomRepository.findAvailableRoomsForDateRange(checkIn, checkOut);
                }
            }
        } else {
            availableRooms = roomRepository.findByStatus("AVAILABLE");
        }

        model.addAttribute("daftarKamar", availableRooms);
        model.addAttribute("roomTypes", roomTypeRepository.findAll());
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("selectedRoomTypeId", roomTypeId);
        model.addAttribute("currentPage", "home");
        model.addAttribute("user", user);

        return "user/home";
    }

    // Booking form
    @GetMapping("/booking/{roomId}")
    public String showBookingForm(@PathVariable Long roomId,
                                  @RequestParam(required = false) LocalDate checkIn,
                                  @RequestParam(required = false) LocalDate checkOut,
                                  Model model,
                                  HttpSession session) {

        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room tidak ditemukan"));

        Booking booking = new Booking();
        booking.setRoom(room);
        booking.setCheckInDate(checkIn);
        booking.setCheckOutDate(checkOut);

        if (checkIn != null && checkOut != null) {
            booking.calculateTotalNights();
            booking.calculateTotalPrice();
        }

        model.addAttribute("booking", booking);
        model.addAttribute("room", room);
        return "user/booking";
    }

    // Submit booking
    @PostMapping("/booking/submit")
    public String submitBooking(@ModelAttribute Booking booking, HttpSession session) {

        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";

        booking.setUser(user);
        booking.setStatus("PENDING");
        booking.calculateTotalNights();
        booking.calculateTotalPrice();
        booking.generateBookingCode();
        booking.setCreatedAt(java.time.LocalDateTime.now());
        booking.setUpdatedAt(java.time.LocalDateTime.now());

        bookingRepository.save(booking);

        return "redirect:/user/my-bookings";
    }

    // My bookings
    @GetMapping("/my-bookings")
    public String myBookings(HttpSession session, Model model) {

        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";

        List<Booking> myBookings = bookingRepository.findByUserOrderByCreatedAtDesc(user);

        for (Booking booking : myBookings) {
            booking.calculateTotalPrice();
        }

        model.addAttribute("bookings", myBookings);
        model.addAttribute("user", user);
        return "user/my-bookings";
    }

    // Booking detail
    @GetMapping("/booking-detail/{bookingId}")
    public String bookingDetail(@PathVariable Long bookingId, Model model, HttpSession session) {

        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking tidak ditemukan"));

        booking.calculateTotalPrice();
        model.addAttribute("booking", booking);
        return "user/booking-detail";
    }

    // Cancel booking
    @PostMapping("/booking/cancel/{bookingId}")
    public String cancelBooking(@PathVariable Long bookingId, HttpSession session) {

        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking tidak ditemukan"));

        if ("PENDING".equals(booking.getStatus())) {
            booking.setStatus("CANCELLED");
            booking.setUpdatedAt(java.time.LocalDateTime.now());
            bookingRepository.save(booking);
        }

        return "redirect:/user/my-bookings";
    }
}
