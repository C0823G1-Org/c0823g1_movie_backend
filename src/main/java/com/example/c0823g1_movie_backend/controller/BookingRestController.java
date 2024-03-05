package com.example.c0823g1_movie_backend.controller;

import com.example.c0823g1_movie_backend.dto.IBookingDTO;
import com.example.c0823g1_movie_backend.service.IBookingService;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.example.c0823g1_movie_backend.config.MailConfig;
import com.example.c0823g1_movie_backend.dto.*;
import com.example.c0823g1_movie_backend.model.Account;
import com.example.c0823g1_movie_backend.model.Movie;
import com.example.c0823g1_movie_backend.model.Schedule;
import com.example.c0823g1_movie_backend.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@RestController
@CrossOrigin("*")
@RequestMapping("/booking")
public class BookingRestController {


    /* Create by: DoLV
     * Date created: 29/02/2024
     * Function: Displays the list and pagination of ticket bookings with a time from the current time about 1 week
     */

    @Autowired
    private IScheduleService scheduleService;

    @Autowired
    private IMovieService movieService;

    @Autowired
    private ITicketService ticketService;

    @Autowired
    private IBookingService iBookingService;

    @Autowired
    private IAccountService accountService;

    @Autowired
    private MailConfig mailConfig;

    @GetMapping(value = {"/", "/list"})
    public ResponseEntity<Page<IBookingDTO>> listBookingTicket( @PageableDefault(size = 4) Pageable pageable ) {
        LocalDateTime time = LocalDateTime.now();
        Page<IBookingDTO> listBookingTicket = iBookingService.findAllBookingTicket(pageable, time);
        if (listBookingTicket.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(listBookingTicket,HttpStatus.OK);
    }

    @GetMapping("historyBooking/{id}")
    public ResponseEntity<Iterable<HistoryBookingDTO>> historyMovie(@PathVariable Long id) {
        return new ResponseEntity<>(iBookingService.historyBooking(id), HttpStatus.OK);
    }

    /*
     * Create by TuanNM
     * Date create: 29/02/2024
     * Method: Display ticket booking history
     * @Param Account ID
     * @Return A list of booking history
     */


    @GetMapping("/search")
    public ResponseEntity<?> searchBookingTicket(
            @RequestParam(value = "searchInput", required = false) String search,
            @RequestParam(value = "date", required = false) LocalDate localDate,
            @PageableDefault(size = 4) Pageable pageable) {
        LocalDateTime timeNow = LocalDateTime.now();
        if (search == null && localDate == null) {
            ApiResponse response = new ApiResponse<>();
            Page<IBookingDTO> listBookingTicketNotFound = iBookingService.findAllBookingTicket(pageable, timeNow);
            response.setData(listBookingTicketNotFound);
            response.setFlag("FOUND");
            return new ResponseEntity<>(response, HttpStatus.OK);
        }

        if (search != null && localDate != null) {
            LocalDateTime dateSearch = localDate.atStartOfDay();
            Page<IBookingDTO> listBookingTicket = iBookingService.searchBookingTicketWithParameterSearchAndDate(search, dateSearch, pageable);
            return getResponseEntity(listBookingTicket, timeNow, pageable);
        } else if (search != null) {
            Page<IBookingDTO> listBookingTicket = iBookingService.searchBookingTicketWithParameterSearch(search, timeNow, pageable);
            return getResponseEntity(listBookingTicket, timeNow, pageable);
        } else {
            LocalDateTime dateSearch = localDate.atStartOfDay();
            Page<IBookingDTO> listBookingTicket = iBookingService.searchBookingTicketWithParameterDate(dateSearch, pageable);
            return getResponseEntity(listBookingTicket, dateSearch, pageable);
        }
    }

    private ResponseEntity<ApiResponse<Page<IBookingDTO>>> getResponseEntity(Page<IBookingDTO> listBookingTicket, LocalDateTime timeNow,Pageable pageable) {
        ApiResponse<Page<IBookingDTO>> response = new ApiResponse<>();
        if (listBookingTicket.isEmpty()) {
            Page<IBookingDTO> listBookingTicketNotFound = iBookingService.findAllBookingTicket(pageable, timeNow);
            response.setFlag("NOT_FOUND");
            response.setData(listBookingTicketNotFound);
        } else {
            response.setFlag("FOUND");
            response.setData(listBookingTicket);
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /* Created by: DoLV
     * Date created: February 29, 2024
     * Function: Select a booking ticket detail . If the booking ticket is not found, it returns the default booking ticket list. If the booking ticket exists and the printing status is false,
     * it returns the booking ticket object to be printed.
     */

    @GetMapping("/exportDetail")
    public ResponseEntity<?> bookingTicketDetail(@RequestParam("idBooking") String id, @PageableDefault(size = 2) Pageable pageable){
        try{
            Long bookingId = parseLong(id);

            IBookingDTO iBookingDTO = iBookingService.findBookingTicketById(bookingId);
            LocalDateTime time = LocalDateTime.now();
            Page<IBookingDTO> listBookingTicket = iBookingService.findAllBookingTicket(pageable,time);

            if (iBookingDTO == null){
                return new ResponseEntity<>(listBookingTicket, HttpStatus.BAD_REQUEST);
            } else {
                if (iBookingDTO.getPrintStatus()){
                    return new ResponseEntity<>(listBookingTicket, HttpStatus.NOT_FOUND);
                } else {
                    List<IBookingDTO> listBookingTicketDetail = iBookingService.listBookingTicketDetail(bookingId);
                    return new ResponseEntity<>(listBookingTicketDetail, HttpStatus.OK);
                }
            }

        } catch (NumberFormatException e){
            return new ResponseEntity<>("Invalid idBooking format", HttpStatus.BAD_REQUEST);
        }
    }

    private Long parseLong(String id) throws NumberFormatException {
        if (!id.matches("\\d+")) {
            throw new NumberFormatException("Invalid idBooking format");
        }
        BigInteger idBigInt = new BigInteger(id);
        if (idBigInt.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw new NumberFormatException("Invalid idBooking format. Number exceeds Long.MAX_VALUE.");
        }
        Long idChange =  idBigInt.longValue();
        if (idChange <= 0L) {
            throw new NumberFormatException("Invalid idBooking format. Number must not be negative.");
        }

        return idChange;
    }


    /* Created by: DoLV
     * Date created: February 29, 2024
     * Function: Print ticket to file pdf. If the booking ticket is not found, it returns the default booking ticket list. If the booking ticket exists and the printing status is false, will print the ticket and set the print status to true.
     */
    @GetMapping("/exportPDF")
    public ResponseEntity<?> bookingTicketExportPDF(@RequestParam("idBooking") String idInput) throws FileNotFoundException, DocumentException {
        Long id = parseLong(idInput);
        IBookingDTO iBookingDTO = iBookingService.findBookingTicketById(id);
        if (iBookingDTO == null){
            return new ResponseEntity<>( HttpStatus.BAD_REQUEST);
        } else {
            if (iBookingDTO.getPrintStatus()){
                return new ResponseEntity<>("printed", HttpStatus.NO_CONTENT);
            } else {
                List<IBookingDTO> listBookingTicketDetail = iBookingService.listBookingTicketDetail(id);
                if (listBookingTicketDetail.isEmpty()){
                    return new ResponseEntity<>( HttpStatus.BAD_REQUEST);
                } else {
                    for (IBookingDTO temp : listBookingTicketDetail){
                        String fileName = "D:\\filePdf\\ticket_" + temp.getBookingCode() + "_MV_"+ temp.getSeatNumber() + ".pdf";
                        float customWidth = 650;
                        float customHeight = 396;
                        Rectangle pageSize = new Rectangle(customWidth, customHeight);
                        Document document = new Document(pageSize, -50, 0, 130, 0);
                        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(fileName));
                        // in
                        try{
                            document.open();
                            document.newPage();
                            addBackgroundAndContent(writer, document, temp);
                            document.close();
                        } catch (DocumentException e) {
                            throw new RuntimeException(e);
                        } catch (FileNotFoundException e) {
                            throw new RuntimeException(e);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    iBookingService.setPrintStatus(id);

                }

                return new ResponseEntity<>( HttpStatus.OK);
            }
        }
    }

    /* Created by: DoLV
     * Date created: February 29, 2024
     * Function: Support for printing pdf files with the function of adding content and background images.
     */

    private void addBackgroundAndContent(PdfWriter writer, Document document, IBookingDTO iBookingDTO ) throws IOException, DocumentException {
        PdfContentByte canvas = writer.getDirectContentUnder();
        Image background = Image.getInstance("D:\\Pictures\\ticket.jpg");
        float documentWidth = document.getPageSize().getWidth();
        float documentHeight = document.getPageSize().getHeight();
        float width = 680;
        float height = 400;
        background.scaleToFit(width, height);
        float x = (documentWidth - background.getScaledWidth()) / 2;
        float yBackground = (documentHeight - background.getScaledHeight() + 250 + 20) / 2;
        background.setAbsolutePosition(0,0);
        Rectangle rectBackground = new Rectangle(
                documentWidth ,
                documentHeight
        );
        rectBackground.setBorder(Rectangle.BOX);
        rectBackground.setBorderWidth(1);
        canvas.rectangle(rectBackground);
        canvas.addImage(background);
        BaseColor color = BaseColor.BLACK;
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 14, color);
//        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, color);

        PdfPTable table = new PdfPTable(2);
        // khoang cach 2 col
        table.setWidthPercentage(65);
        table.setTotalWidth(width);
        float yTable = yBackground - 70;

        table.writeSelectedRows(0, 0, x, yTable, writer.getDirectContent());
        table.addCell(createCell("Movie: " + iBookingDTO.getNameMovieFilm(),font));
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//        String formattedDateTime = ticket.getStartTime().format(formatter);
        table.addCell(createCell("Show Time: " + iBookingDTO.getScheduleTime(), font));
        table.addCell(createCell("Customer: " + iBookingDTO.getNameCustomer(), font));
        table.addCell(createCell("Seat Number: " + iBookingDTO.getSeatNumber(), font));
        table.addCell(createCell("Ticket Price:" + iBookingDTO.getTicketPrice(), font));
        table.addCell(createCell("Room Number: " + iBookingDTO.getCinemaHall(), font));
        table.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        document.add(table);
    }
    private PdfPCell createCell(String content, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(content, font));
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }


    @GetMapping("searchMovieBooking/{start}/{end}")
    public ResponseEntity<Iterable<HistoryBookingDTO>> searchMovieBooking(@PathVariable LocalDateTime startDate, @PathVariable LocalDateTime endDate) {
        return new ResponseEntity<>(iBookingService.searchBookingByDate(startDate, endDate), HttpStatus.OK);
    }
    /**
     * Create by TuanNM
     * Date create: 29/02/2024
     * Method: Search by start date and end date
     * @param startDate is the starting date
     * @param endDate is the end date
     * @return a search list
     */


    /*
     * Create by HaiNT
     * Date create: 29/02/2024
     * Method: Receive request from the client when clicking
     * to book a ticket and return information to the Booking Confirmation page
     * @Param ticket(idMovie,scheduleId,seatList)
     * @Return object bookingDTO(image,movieName,screen,movieDate,timeStart,seat,price,sum)
     */
    @PostMapping("/confirm")
    public ResponseEntity<BookingDTO> returnInformationTicketBooking(@RequestBody TicketDTO ticket) {
        System.out.println("abc");
        Optional<Movie> movie = Optional.ofNullable(movieService.findMovieById(ticket.getIdMovie()));
        Optional<Schedule> schedule = scheduleService.getScheduleById(ticket.getScheduleId());
        List<Integer> seatList = ticket.getSeatList();

        String image = movie.get().getPoster();
        String movieName = movie.get().getName();
        String screen = schedule.get().getHall().getName();
        LocalDate date = schedule.get().getDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String movieDate = date.format(formatter);
        LocalTime time = schedule.get().getScheduleTime().getScheduleTime();
        List<String> seat = new ArrayList<>();

        for (Integer s: seatList) {
            String result = "";
            String ss = s.toString();
            if (s<=10){
                result = "A" + s;
            }else if (s<=20){
                if (s==20){
                    result = "B10";
                }else {
                    result = "B" + ss.charAt(ss.length()-1);
                }

            }else if (s<=30){
                if (s==30){
                    result = "C10";
                }else {
                    result = "C" + ss.charAt(ss.length()-1);
                }
            }else if (s<=40){
                if (s==40){
                    result = "D10";
                }else {
                    result = "D" + ss.charAt(ss.length()-1);
                }
            }else if (s<=50){
                if (s==50){
                    result = "E10";
                }else {
                    result = "E" + ss.charAt(ss.length()-1);
                }
            }

            seat.add(result);
        }

        Integer price = movie.get().getTicketPrice();

        Long sum = (long) (seat.size() * price);

        String timeStart = time.toString().substring(0,5);
        System.out.println(timeStart);

        Account account = accountService.findAccountById(ticket.getAccountId());
        String email = account.getEmail();


        BookingDTO bookingDTO = new BookingDTO(image,movieName,screen,movieDate,timeStart,seat,price,sum,email);



        return new ResponseEntity<>(bookingDTO, HttpStatus.OK);
    }


    /*
     * Create by HaiNT
     * Date create: 29/02/2024
     * Method: Receive request from the client when checkout successfully
     * @RequestBody checkoutDTO(accountId,scheduleId,seatList)
     * @Return status
     */
    @PostMapping ("/checkout")
    public ResponseEntity<CheckoutDTO> checkout(@RequestBody CheckoutDTO checkoutDTO){
        LocalDateTime date = LocalDateTime.now();
        iBookingService.saveBooking(checkoutDTO.getAccountId(),date);
        Integer id = iBookingService.getBooking();
        Long scheduleId = checkoutDTO.getScheduleId();
        for (Integer seat : checkoutDTO.getSeatNumber()) {
            ticketService.saveTicket(seat,id,scheduleId);
        }
        Schedule schedule = scheduleService.getScheduleById(checkoutDTO.getScheduleId()).get();
        Account account = accountService.findAccountById(checkoutDTO.getAccountId());
        String email = account.getEmail();
        String seat = "";


        for (Integer s: checkoutDTO.getSeatNumber()) {
            String result = "";
            String ss = s.toString();
            if (s<=10){
                result = "A" + s;
            }else if (s<=20){
                if (s==20){
                    result = "B10";
                }else {
                    result = "B" + ss.charAt(ss.length()-1);
                }

            }else if (s<=30){
                if (s==30){
                    result = "C10";
                }else {
                    result = "C" + ss.charAt(ss.length()-1);
                }
            }else if (s<=40){
                if (s==40){
                    result = "D10";
                }else {
                    result = "D" + ss.charAt(ss.length()-1);
                }
            }else if (s<=50){
                if (s==50){
                    result = "E10";
                }else {
                    result = "E" + ss.charAt(ss.length()-1);
                }
            }

            seat += result;
        }

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(email);
        mailMessage.setSubject("Bạn đã đặt vé xem phim thành công");
        String content = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Document</title>\n" +
                "</head>\n" +
                "\n" +
                "<body style=\"width: 80%;;margin-left: 10%;\">\n" +
                "    <img style=\"width: 50%;height: 30%;margin-left: 25%;\"\n" +
                "        src=\""+ schedule.getMovie().getPoster() +"\" alt=\"Madame Web\n" +
                "    \n" +
                "    Xem thêm tại: https://www.galaxycine.vn/\">\n" +
                "    <table style=\"width: 100%;padding-left: 20%;\">\n" +
                "        <tr>\n" +
                "            <td>Phòng chiếu</th>\n" +
                "            <td>"+schedule.getHall().getName()+"</td>\n" +
                "        </tr>\n" +
                "        <tr>\n" +
                "            <td>Ngày chiếu</th>\n" +
                "            <td>"+schedule.getDate()+"</td>\n" +
                "        </tr>\n" +
                "\n" +
                "        <tr>\n" +
                "            <td>Giờ chiếu</th>\n" +
                "            <td>"+ schedule.getScheduleTime().getScheduleTime()+"</td>\n" +
                "        </tr>\n" +
                "        <tr>\n" +
                "            <td>Ghế </th>\n" +
                "            <td>"+
                seat
                +"</td>\n" +
                "        </tr>\n" +
                "    </table>\n" +
                "    <img style=\"width: 30%;height: 30%;margin-left: 35%;\"\n" +
                "        src=\"https://t3.gstatic.com/licensed-image?q=tbn:ANd9GcSh-wrQu254qFaRcoYktJ5QmUhmuUedlbeMaQeaozAVD4lh4ICsGdBNubZ8UlMvWjKC\"\n" +
                "        alt=\"\">\n" +
                "</body>\n" +
                "\n" +
                "</html>";
        mailMessage.setText(content);
        mailConfig.getJavaMailSender().send(mailMessage);


        return new ResponseEntity<>(HttpStatus.CREATED);
    }


}
