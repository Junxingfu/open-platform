import { Link } from 'react-router-dom'
import { Icon } from '../components/Icon'

export function NotFoundPage() {
  return (
    <div className="not-found">
      <div className="not-found-code">404</div>
      <h1>页面走丢了</h1>
      <p>你访问的页面不存在，或链接已经失效。</p>
      <Link to="/dashboard" className="button button-primary">
        返回工作台 <Icon name="arrowRight" size={15} />
      </Link>
    </div>
  )
}
