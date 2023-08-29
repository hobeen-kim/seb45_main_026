import { useMemo } from 'react';
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { setBrowserWidth } from './redux/createSlice/UISettingSlice';
import MainPage from './pages/contents/MainPage';
import LoginPage from './pages/auth/LoginPage';
import MyProfilePage from './pages/userInfo/MyProfilePage';
import Header, { MainPageHeader } from './components/headers/Header';
import Footer from './components/footers/Footer';
import './App.css'

function App() {
  const dispatch = useDispatch();
  const location = useSelector(state=>state.uiSetting.location);
  
  const handleResize = () => {
      dispatch(setBrowserWidth(window.innerWidth));
  }

  useMemo(()=>{
    window.addEventListener('resize',handleResize);
  },[]);

  return (
      <BrowserRouter>
        {location==='/'?<MainPageHeader/>:<Header/>}
        <Routes>
          <Route path="/" element={<MainPage/>}/>
          <Route path="/login" element={<LoginPage/>}/>
          <Route path="/myprofile" element={<MyProfilePage/>}/>
        </Routes>
        {location==='/'?null:<Footer/>}
      </BrowserRouter>
  );
}

export default App;
