import SongCard from './SongCard'
import Skeleton from '../UI/Skeleton'

const SongList = ({ songs, loading = false, title }) => {
  if (loading) {
    return (
      <div>
        {title && <h2 className="text-2xl font-bold mb-4">{title}</h2>}
        <div className="space-y-3">
          {[...Array(5)].map((_, i) => (
            <Skeleton key={i} variant="card" className="h-20" />
          ))}
        </div>
      </div>
    )
  }
  
  if (!songs || songs.length === 0) {
    return (
      <div>
        {title && <h2 className="text-2xl font-bold mb-4">{title}</h2>}
        <p className="text-dark-400 text-center py-8">No songs found</p>
      </div>
    )
  }
  
  return (
    <div>
      {title && <h2 className="text-2xl font-bold mb-4">{title}</h2>}
      <div className="space-y-3">
        {songs.map((song, index) => (
          <SongCard key={song.videoId || index} song={song} index={index} />
        ))}
      </div>
    </div>
  )
}

export default SongList

