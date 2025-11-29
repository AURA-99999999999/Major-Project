const Skeleton = ({ className = '', variant = 'default' }) => {
  const variants = {
    default: 'skeleton h-4 rounded',
    text: 'skeleton h-4 rounded w-full',
    circle: 'skeleton rounded-full',
    image: 'skeleton rounded-lg aspect-square',
    card: 'skeleton rounded-xl h-64',
  }
  
  return <div className={`${variants[variant]} ${className}`} />
}

export default Skeleton

