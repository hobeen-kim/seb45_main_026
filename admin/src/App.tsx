import React from "react";
import "./App.css";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import LoginPage from "./pages/LoginPage";
import ReportVideoPage from "./pages/reports/ReportVideoPage";
import Header from "./components/header/Header";
import Footer from "./components/Footer";
import VideoPage from "./pages/VideoPage";
import MemberPage from "./pages/MemberPage";
import ReportReviewPage from "./pages/reports/ReportReviewPage";
import ReportChannelPage from "./pages/reports/ReportChannelPage";
import ReportNoticePage from "./pages/reports/ReportNoticePage";

function App() {
  return (
    <BrowserRouter>
      <Header />
      <Routes>
        <Route path="/" element={<VideoPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/members" element={<MemberPage />} />
        <Route path="/reports/videos" element={<ReportVideoPage />} />
        <Route path="/reports/reviews" element={<ReportReviewPage />} />
        <Route path="/reports/channels" element={<ReportChannelPage />} />
        <Route path="/reports/notices" element={<ReportNoticePage />} />
      </Routes>
      <Footer />
    </BrowserRouter>
  );
}

export default App;
