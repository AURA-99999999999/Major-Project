import { useLocation } from 'react-router-dom'
import Sidebar from './Sidebar'
import Navbar from './Navbar'
import MiniPlayer from '../Player/MiniPlayer'

const Layout = ({ children }) => {
  const location = useLocation()
  const hideSidebar = ['/login', '/register'].includes(location.pathname)

  return (
    <div className="flex h-screen overflow-hidden bg-dark-50">
      {!hideSidebar && <Sidebar />}
      
      <div className="flex-1 flex flex-col overflow-hidden">
        {!hideSidebar && <Navbar />}
        
        <main className="flex-1 overflow-y-auto scrollbar-thin pb-24">
          {children}
        </main>
        
        {!hideSidebar && <MiniPlayer />}
      </div>
    </div>
  )
}

export default Layout

